package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.PowerManager
import android.util.Log
import android.util.Size
import app.signsetu.BuildConfig
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.ui.layout.ContentScale
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as Extent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max

/** Whether the traced skeleton is drawn over the camera picture; the picture itself always shows. */
object SkeletonOverlay {
    val on = mutableStateOf(true)
}

/** Whether a skeleton snapshot (biometric landmarks) is kept in the record when a signed answer is logged; off unless
 * the officer turns it on, and off again with every new session. */
object EvidenceMode {
    val autoCapture = mutableStateOf(false)
}

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are
 * bound: no image is captured, recorded or saved. */
@Composable
fun CameraMirror(
    tracker: LandmarkTracker?,
    modifier: Modifier = Modifier,
    onFrame: ((LandmarkFrame) -> Unit)? = null,
    handColor: Color = Color(0xFFFFC56B),
    showFace: Boolean = false,
    showSkeleton: Boolean = true,
    /** A nod or shake, on the analysis thread; the stage treats it as a proposal, never an answer. */
    onHead: ((Head) -> Unit)? = null,
    /** A body part pointed at, as a card id; also only a proposal. */
    onPoint: ((String) -> Unit)? = null,
    onStopPalm: (() -> Unit)? = null,
    onShrug: (() -> Unit)? = null,
    /** A courtesy held on the analysis thread (namaste, a wave, thanks, a bow); the stage treats it as a proposal. */
    onEveryday: ((Everyday) -> Unit)? = null,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var shown by remember { mutableStateOf<LandmarkFrame?>(null) }
    val frameSink by rememberUpdatedState(onFrame)
    val headSink by rememberUpdatedState(onHead)
    val pointSink by rememberUpdatedState(onPoint)
    val stopSink by rememberUpdatedState(onStopPalm)
    val shrugSink by rememberUpdatedState(onShrug)
    val everydaySink by rememberUpdatedState(onEveryday)
    var front by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val permitted = remember { hasCameraPermission(context) }

    if (permitted) {
        // CameraX binds and sets surfaces on the main thread only; an effect resumes wherever its dispatcher says.
        LaunchedEffect(owner, tracker) { withContext(Dispatchers.Main.immediate) {
            // Opening the camera waits for the stage's first frame, so the question appears first.
            withFrameNanos { }
            // A camera service that fails to start, or a camera another app holds, shows the placeholder instead of crashing.
            val (provider, selector) = try {
                val provider = ProcessCameraProvider.awaitInstance(context)
                provider to when {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA.also { front = true }
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA.also { front = false }
                    else -> { failed = true; return@withContext }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                return@withContext
            }
            val preview = Preview.Builder().setResolutionSelector(PREVIEW_RESOLUTION).build().apply { setSurfaceProvider { request = it } }
            val frames = tracker?.let {
                ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    // CameraX turns the frame upright into its own reused buffer, cheaper than a bitmap rotate per frame.
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ANALYSIS_RESOLUTION)
                    .build()
            }
            val useCases = listOfNotNull<UseCase>(preview, frames)
            try {
                provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                analysis = frames
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                analysis = null
                request = null
                provider.unbind(*useCases.toTypedArray())
            }
        } }
    }

    val analysing = onFrame != null
    DisposableEffect(analysis, tracker, analysing) {
        val a = analysis
        if (a == null || tracker == null) return@DisposableEffect onDispose { shown = null }
        var lastPerson: Long? = null
        val pipeline = FramePipeline()
        val blur = BlurMeter()
        val executor = Executors.newSingleThreadExecutor()
        var lastMs: Long? = null
        var reused: Bitmap? = null
        // Heard on the analysis thread, so the analyzer reads it without a lock. Severe heat slows the idle mirror first.
        var hot = false
        val power = context.getSystemService(PowerManager::class.java)
        val thermal = PowerManager.OnThermalStatusChangedListener { hot = it >= PowerManager.THERMAL_STATUS_SEVERE }
        power?.addThermalStatusListener(executor, thermal)
        a.setAnalyzer(executor) { proxy ->
            proxy.use {
                val ms = it.imageInfo.timestamp / 1_000_000
                // An empty room outside a sign window is checked about three times a second, to save battery and heat.
                val idle = lastPerson.let { seenAt -> seenAt == null || ms - seenAt > IDLE_AFTER_MS }
                val interval = if (analysing) ANALYSIS_INTERVAL_MS else if (idle || hot) IDLE_INTERVAL_MS else PREVIEW_INTERVAL_MS
                if (!analysisDue(lastMs, ms, interval)) return@setAnalyzer
                lastMs = ms
                val plane = it.planes[0]
                val packed = plane.pixelStride == 4 && plane.rowStride == it.width * 4
                val bitmap = if (packed) {
                    (reused?.takeIf { b -> b.width == it.width && b.height == it.height }
                        ?: createBitmap(it.width, it.height).also { b -> reused?.recycle(); reused = b })
                        .apply { plane.buffer.rewind(); copyPixelsFromBuffer(plane.buffer) }
                } else {
                    it.toBitmap()
                }
                val aspect = bitmap.width.toFloat() / bitmap.height
                // Picture quality is judged only while a sign is being read; elsewhere it costs nothing.
                val blurry = analysing && blur.blurry(bitmap)
                val frame = runCatching { tracker.track(bitmap, ms)?.let { raw -> pipeline.mark(raw, aspect, blurry) } }.getOrNull()
                if (!packed) bitmap.recycle()
                if (frame?.pose != null) lastPerson = ms
                shown = frame
                frame?.let { f -> frameSink?.invoke(f) }
                // Inside a sign window open hands and pointing are signs, not answers.
                if (!analysing) {
                    frame?.head?.let { h -> headSink?.invoke(h) }
                    frame?.pointsAt?.let { c -> pointSink?.invoke(c) }
                    if (frame?.stopPalm == true) stopSink?.invoke()
                    if (frame?.shrug == true) shrugSink?.invoke()
                    frame?.everyday?.let { g -> everydaySink?.invoke(g) }
                }
            }
        }
        onDispose {
            power?.removeThermalStatusListener(thermal)
            a.clearAnalyzer()
            executor.execute { reused?.recycle() }
            executor.shutdown()
            shown = null
        }
    }

    val surface = request
    if (!permitted || failed || surface == null) {
        Box(modifier) { placeholder() }
        return
    }
    val labels = rememberTextMeasurer()
    Box(modifier) {
        // TextureView-backed, so the rounded window around it clips the picture. Fit: the subject sees all the camera sees.
        CameraXViewfinder(surface, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED, contentScale = ContentScale.Fit)
        // Landmarks are read in the draw phase only, so each analysed frame redraws this canvas without recomposing.
        Canvas(Modifier.fillMaxSize()) {
            if (!showSkeleton || !SkeletonOverlay.on.value) return@Canvas
            // No body in view while signing is expected: a faint head and shoulders shows where to stand, without words.
            if (analysing && shown?.pose == null) {
                val c = Offset(size.width / 2, size.height * 0.38f)
                val r = size.minDimension * 0.12f
                val guide = Color.White.copy(alpha = 0.35f)
                val line = Stroke(size.minDimension / 80f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
                drawCircle(guide, r, c, style = line)
                drawPath(Path().apply {
                    moveTo(c.x - r * 2.6f, size.height * 0.85f)
                    quadraticTo(c.x - r * 2.4f, c.y + r * 1.6f, c.x, c.y + r * 1.4f)
                    quadraticTo(c.x + r * 2.4f, c.y + r * 1.6f, c.x + r * 2.6f, size.height * 0.85f)
                }, guide, style = line)
            }
            val seen = shown ?: return@Canvas
            drawLandmarks(seen, front, analysing, showFace, handColor, labels)
        }
    }
}

/** The traced skeleton, brows and lips of [seen] over a frame drawn Fit and centred; the camera overlay and its device test both draw this. */
internal fun DrawScope.drawLandmarks(seen: LandmarkFrame, front: Boolean, analysing: Boolean, showFace: Boolean, handColor: Color, labels: TextMeasurer) {
    fun map(p: Offset) = overlayPoint(p, seen.aspect, size.width, size.height, front)
    val a = map(Offset.Zero)
    val b = map(Offset(1f, 1f))
    val stroke = max(4f, size.minDimension / 90f)
    // A wrist at or past the frame edge lights that edge, a wordless "bring your hands into view", only while signing is expected.
    if (analysing) seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
        val bar = stroke * 2f
        val left = minOf(a.x, b.x); val right = max(a.x, b.x)
        for (w in listOf(pose[15], pose[16])) {
            val e = outOfView(w) ?: continue
            val (x, y) = when (e) {
                Edge.Top -> Offset(left, a.y) to Extent(right - left, bar)
                Edge.Bottom -> Offset(left, b.y - bar) to Extent(right - left, bar)
                // The front camera is mirrored, so the frame's left edge shows on the right.
                Edge.Left -> (if (front) Offset(right - bar, a.y) else Offset(left, a.y)) to Extent(bar, b.y - a.y)
                Edge.Right -> (if (front) Offset(left, a.y) else Offset(right - bar, a.y)) to Extent(bar, b.y - a.y)
            }
            drawRect(handColor.copy(alpha = 0.8f), x, y)
        }
    }
    // Points beyond the frame edge would otherwise sit in the letterbox band as stray dots.
    clipRect(minOf(a.x, b.x), a.y, max(a.x, b.x), b.y) {
        seen.face?.takeIf { it.size >= 468 }?.let { face ->
            fun outline(line: List<Int>, color: Color, width: Float) {
                val path = Path().apply { line.forEachIndexed { i, n -> map(face[n]).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
                drawPath(path, color, style = Stroke(width))
            }
            if (showFace) for (line in FaceOutlines.all) outline(line, Color.White.copy(alpha = 0.5f), stroke * 0.5f)
            // Brows and lips carry facial grammar; they light up when raised or open.
            val brow = if (seen.browsUp) handColor else Color.White.copy(alpha = 0.7f)
            outline(FaceOutlines.leftBrow, brow, stroke * if (seen.browsUp) 0.9f else 0.6f)
            outline(FaceOutlines.rightBrow, brow, stroke * if (seen.browsUp) 0.9f else 0.6f)
            outline(FaceOutlines.lips, if (seen.mouthOpen) handColor else Color.White.copy(alpha = 0.7f), stroke * if (seen.mouthOpen) 0.9f else 0.6f)
        }
        seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
            for ((i, j) in ArmBones) drawLine(handColor.copy(alpha = 0.6f), map(pose[i]), map(pose[j]), stroke * 1.6f, StrokeCap.Round)
            for (i in 11..16) drawCircle(handColor, stroke * 1.2f, map(pose[i]))
        }
        for ((label, hand) in labelledHands(seen)) {
            for ((i, j) in HandBones) drawLine(handColor, map(hand[i]), map(hand[j]), stroke * 1.3f, StrokeCap.Round)
            for (p in hand) { drawCircle(Color.Black.copy(alpha = 0.5f), stroke * 1.4f, map(p)); drawCircle(Color.White, stroke * 1.1f, map(p)) }
            drawText(labels, label, map(hand[0]) + Offset(stroke * 1.5f, stroke), HandLabelStyle)
        }
        // A wordless mark of the gesture just read, for subjects who cannot read the caption.
        val cue = when {
            seen.head == Head.Nod -> Color(0xFF22C55E) to "✓"
            seen.head == Head.Shake -> Color(0xFFEF4444) to "✕"
            seen.stopPalm -> Color(0xFFF59E0B) to "✋"
            else -> null
        }
        cue?.let { (color, glyph) ->
            drawCircle(color, stroke * 3f, Offset(size.width / 2, size.height * 0.12f))
            drawText(labels, glyph, Offset(size.width / 2 - stroke * 1.2f, size.height * 0.12f - stroke * 1.8f), CueStyle)
        }
    }
    // Debug builds show the raw cue values for calibrating on a real phone: adb shell setprop log.tag.SignSetuTuning DEBUG
    if (BuildConfig.DEBUG && Log.isLoggable("SignSetuTuning", Log.DEBUG)) drawText(labels, tuningLine(seen), Offset(8f, 8f), TuningStyle)
}

private val HandLabelStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val CueStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
private val TuningStyle = TextStyle(color = Color.Yellow, fontSize = 11.sp, background = Color.Black.copy(alpha = 0.5f))

internal enum class Edge { Top, Bottom, Left, Right }

/** The frame edge a pose point has reached (within 3%) or passed, in unmirrored frame terms. */
internal fun outOfView(p: Offset): Edge? = when {
    p.y > 0.97f -> Edge.Bottom
    p.y < 0.03f -> Edge.Top
    p.x < 0.03f -> Edge.Left
    p.x > 0.97f -> Edge.Right
    else -> null
}

/** The subject's own sides, as Holistic names them: on the mirrored front camera their left hand shows on the left. */
internal fun labelledHands(frame: LandmarkFrame): List<Pair<String, List<Offset>>> =
    listOfNotNull(frame.leftHand?.takeIf { it.size >= 21 }?.let { "L" to it }, frame.rightHand?.takeIf { it.size >= 21 }?.let { "R" to it })

/** Where a normalised landmark lands in a [viewW] x [viewH] view showing the frame letterboxed (Fit) and
 * centred. */
internal fun overlayPoint(p: Offset, frameAspect: Float, viewW: Float, viewH: Float, mirrored: Boolean, rotation: Int = 0): Offset {
    val (x, y) = when (rotation.mod(360)) {
        90 -> 1f - p.y to p.x
        180 -> 1f - p.x to 1f - p.y
        270 -> p.y to 1f - p.x
        else -> p.x to p.y
    }
    val aspect = if (rotation.mod(180) == 0) frameAspect else 1f / frameAspect
    val w = minOf(viewW, viewH * aspect)
    val h = w / aspect
    return Offset((viewW - w) / 2f + (if (mirrored) 1f - x else x) * w, (viewH - h) / 2f + y * h)
}

// About 33 frames a second inside a sign window, 30 in preview, 4 in an empty room.
private const val ANALYSIS_INTERVAL_MS = 30L
private const val PREVIEW_INTERVAL_MS = 33L
private const val IDLE_INTERVAL_MS = 250L
private const val IDLE_AFTER_MS = 10_000L

/** A sentinel such as Long.MIN_VALUE overflows the subtraction and would skip every frame, so the first one is null. */
internal fun analysisDue(lastMs: Long?, ms: Long, interval: Long = ANALYSIS_INTERVAL_MS) = lastMs == null || ms - lastMs >= interval
// Preview and analysis share one tall 16:9 shape, so the skeleton lines up and a portrait window shows the whole frame.
private val PREVIEW_RESOLUTION = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()

private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(848, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

/** The shape of the upright camera frame; windows that show the camera use it so nothing is cropped or letterboxed. */
const val CAMERA_ASPECT = 9f / 16f

/** Mouth, brow, pointing, palm, shrug and thumb readings of one frame, as tuned by the constants in LandmarkTracker.kt. */
internal fun tuningLine(f: LandmarkFrame): String {
    val (mouth, brows) = faceCues(f) ?: (Float.NaN to Float.NaN)
    val thumbs = f.hands.mapNotNull { thumb(it, f.aspect) }
    return "mouth %.2f brow %.2f%s%s | point %s palm %s shrug %s thumb %s".format(
        mouth, brows, if (f.browsUp) " UP" else "", if (f.mouthOpen) " OPEN" else "",
        region(f) ?: "-", raisedPalm(f), shrug(f), thumbs.joinToString().ifEmpty { "-" },
    )
}

/** The arms and hands of [seen] as a PNG, the skeleton snapshot kept with a signed answer when evidence mode is on. */
fun renderSkeletonPng(seen: LandmarkFrame, width: Int = 360, height: Int = 480): ByteArray {
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(30, 42, 51))
    val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(255, 197, 107)
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    fun line(a: Offset, b: Offset) = canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, bone)
    fun dot(p: Offset, r: Float) = canvas.drawCircle(p.x * width, p.y * height, r, joint)

    seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
        for ((i, j) in ArmBones) line(pose[i], pose[j])
        for (i in 11..16) dot(pose[i], 7f)
    }
    for (hand in seen.hands) {
        if (hand.size < 21) continue
        for ((i, j) in HandBones) line(hand[i], hand[j])
        for (p in hand) dot(p, 4.5f)
    }
    return try {
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()
    } finally {
        bitmap.recycle()
    }
}


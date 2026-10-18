package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.background
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max

/** Shows only the traced skeleton, never the picture: for subjects upset by their own face, and bystanders. */
object SkeletonOnly {
    val on = mutableStateOf(false)
}

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are bound:
 * no image is captured, recorded or saved (D-20). The skeleton, brows and lips are drawn whenever the camera shows;
 * frames go to [onFrame] for sign capture only inside a sign window (D-25), which also raises the analysis rate. The camera stops when this leaves the screen. [placeholder] shows instead when there
 * is no permission or no camera.
 */
@Composable
fun CameraMirror(
    tracker: LandmarkTracker?,
    modifier: Modifier = Modifier,
    onFrame: ((LandmarkFrame) -> Unit)? = null,
    handColor: Color = Color(0xFFFFC56B),
    showFace: Boolean = false,
    /** A nod or shake, on the analysis thread; the stage treats it as a proposal, never an answer. */
    onHead: ((Head) -> Unit)? = null,
    /** A body part pointed at, as a card id; also only a proposal. */
    onPoint: ((String) -> Unit)? = null,
    onStopPalm: (() -> Unit)? = null,
    onShrug: (() -> Unit)? = null,
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
    var front by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val permitted = remember { hasCameraPermission(context) }
    val skeletonOnly = SkeletonOnly.on.value && tracker != null

    if (permitted) {
        // CameraX binds and sets surfaces on the main thread only; an effect resumes wherever its dispatcher says.
        LaunchedEffect(owner, tracker, skeletonOnly) { withContext(Dispatchers.Main.immediate) {
            // Opening the camera waits for the stage's first frame, so the question appears first (NFR-02).
            withFrameNanos { }
            val provider = ProcessCameraProvider.awaitInstance(context)
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA.also { front = true }
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA.also { front = false }
                else -> { failed = true; return@withContext }
            }
            val preview = if (skeletonOnly) null else Preview.Builder().setResolutionSelector(PREVIEW_RESOLUTION).build().apply { setSurfaceProvider { request = it } }
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
            } catch (e: IllegalArgumentException) {
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
        val cues = FaceCueTracker()
        val heads = HeadGestures()
        val thumbs = ThumbGestures()
        val pointing = BodyPointing()
        val palms = PalmGestures()
        val shrugs = ShrugGestures()
        val executor = Executors.newSingleThreadExecutor()
        var lastMs: Long? = null
        var reused: Bitmap? = null
        var busy = false
        a.setAnalyzer(executor) { proxy ->
            proxy.use {
                val ms = it.imageInfo.timestamp / 1_000_000
                // An empty room outside a sign window is checked about three times a second, to save battery and heat.
                val idle = lastPerson.let { it == null || ms - it > IDLE_AFTER_MS }
                val interval = if (analysing) ANALYSIS_INTERVAL_MS else if (idle) IDLE_INTERVAL_MS else PREVIEW_INTERVAL_MS
                if (busy || !analysisDue(lastMs, ms, interval)) return@setAnalyzer
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
                busy = true
                val frame = runCatching { tracker.track(bitmap, ms)?.copy(aspect = aspect)?.let(cues::mark)?.let(heads::mark)?.let(thumbs::mark)?.let(pointing::mark)?.let(palms::mark)?.let(shrugs::mark) }.getOrNull()
                busy = false
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
                }
            }
        }
        onDispose {
            a.clearAnalyzer()
            executor.execute { reused?.recycle() }
            executor.shutdown()
            shown = null
        }
    }

    val surface = request
    if (!permitted || failed || (surface == null && !skeletonOnly)) {
        Box(modifier) { placeholder() }
        return
    }
    val labels = rememberTextMeasurer()
    Box(modifier) {
        // TextureView-backed, so the rounded window around it clips the picture. Fit: the subject sees all the camera sees.
        if (surface != null && !skeletonOnly) CameraXViewfinder(surface, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED, contentScale = ContentScale.Fit)
        else Box(Modifier.fillMaxSize().background(Color(0xFF1E2A33)))
        // Landmarks are read in the draw phase only, so each analysed frame redraws this canvas without recomposing.
        Canvas(Modifier.fillMaxSize()) {
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
            drawText(labels, label, map(hand[0]) + Offset(stroke * 1.5f, stroke), TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
    // Debug builds show the raw cue values, for setting the calibration knobs on a real phone.
    if (BuildConfig.DEBUG) drawText(labels, tuningLine(seen), Offset(8f, 8f), TextStyle(color = Color.Yellow, fontSize = 11.sp, background = Color.Black.copy(alpha = 0.5f)))
}

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

/**
 * Where a normalised landmark lands in a [viewW] x [viewH] view showing the frame letterboxed (Fit) and centred.
 * [frameAspect] is the analysed frame's width / height; [rotation] is the clockwise turn that makes it upright.
 */
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

// Up to 30 fps inside a sign window: fast signing blurs between frames at 15. Outside it the overlay runs at 10 fps to bound heat.
private const val ANALYSIS_INTERVAL_MS = 30L
private const val PREVIEW_INTERVAL_MS = 100L
private const val IDLE_INTERVAL_MS = 300L
private const val IDLE_AFTER_MS = 10_000L

/** A sentinel such as Long.MIN_VALUE overflows the subtraction and would skip every frame, so the first one is null. */
internal fun analysisDue(lastMs: Long?, ms: Long, interval: Long = ANALYSIS_INTERVAL_MS) = lastMs == null || ms - lastMs >= interval
// Preview and analysis share one tall 16:9 shape, so the skeleton lines up and a portrait window shows the whole frame.
private val PREVIEW_RESOLUTION = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()

private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
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

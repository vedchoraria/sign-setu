package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import kotlin.math.max

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are bound:
 * no image is captured, recorded or saved (D-20). Frames are analysed only while [onFrame] is set, inside a sign window
 * (D-25); the analysis use case stays bound, so opening a window never restarts the camera. Each frame's landmarks go
 * to [onFrame] for sign capture. The camera stops when this leaves the screen. [placeholder] shows instead when there
 * is no permission or no camera.
 */
@Composable
fun CameraMirror(
    tracker: LandmarkTracker?,
    modifier: Modifier = Modifier,
    onFrame: ((LandmarkFrame) -> Unit)? = null,
    handColor: Color = Color(0xFFFFC56B),
    showFace: Boolean = false,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var shown by remember { mutableStateOf<LandmarkFrame?>(null) }
    val frameSink by rememberUpdatedState(onFrame)
    var front by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val permitted = remember { hasCameraPermission(context) }

    if (permitted) {
        LaunchedEffect(owner, tracker) {
            // Opening the camera waits for the stage's first frame, so the question appears first (NFR-02).
            withFrameNanos { }
            val provider = ProcessCameraProvider.awaitInstance(context)
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA.also { front = true }
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA.also { front = false }
                else -> { failed = true; return@LaunchedEffect }
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
            } catch (e: IllegalArgumentException) {
                failed = true
            } finally {
                analysis = null
                provider.unbind(*useCases.toTypedArray())
            }
        }
    }

    val analysing = onFrame != null
    DisposableEffect(analysis, tracker, analysing) {
        val a = analysis
        if (a == null || tracker == null || !analysing) return@DisposableEffect onDispose { shown = null }
        val executor = Executors.newSingleThreadExecutor()
        var lastMs: Long? = null
        var reused: Bitmap? = null
        var busy = false
        var fpsFrames = 0
        var fpsSince = SystemClock.uptimeMillis()
        a.setAnalyzer(executor) { proxy ->
            proxy.use {
                val ms = it.imageInfo.timestamp / 1_000_000
                if (busy || !analysisDue(lastMs, ms)) return@setAnalyzer
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
                val frame = runCatching { tracker.track(bitmap, ms)?.copy(aspect = aspect) }.getOrNull()
                busy = false
                if (BuildConfig.DEBUG) {
                    fpsFrames++
                    val now = SystemClock.uptimeMillis()
                    if (now - fpsSince >= 1000) {
                        Log.d("SignSetuFps", "$fpsFrames fps")
                        fpsFrames = 0
                        fpsSince = now
                    }
                }
                if (!packed) bitmap.recycle()
                shown = frame
                frame?.let { f -> frameSink?.invoke(f) }
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
            val seen = shown ?: return@Canvas
            fun map(p: Offset) = overlayPoint(p, seen.aspect, size.width, size.height, front)
            val a = map(Offset.Zero)
            val b = map(Offset(1f, 1f))
            val stroke = max(4f, size.minDimension / 90f)
            // Points beyond the frame edge would otherwise sit in the letterbox band as stray dots.
            clipRect(minOf(a.x, b.x), a.y, max(a.x, b.x), b.y) {
                if (showFace) seen.face?.takeIf { it.size >= 468 }?.let { face ->
                    for (line in FaceOutlines.all) {
                        val path = Path().apply { line.forEachIndexed { i, n -> map(face[n]).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
                        drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(stroke * 0.5f))
                    }
                }
                seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
                    for ((i, j) in ArmBones) drawLine(handColor.copy(alpha = 0.6f), map(pose[i]), map(pose[j]), stroke * 1.6f, StrokeCap.Round)
                    for (i in 11..16) drawCircle(handColor, stroke * 1.2f, map(pose[i]))
                }
                for ((label, hand) in labelledHands(seen)) {
                    for ((i, j) in HandBones) drawLine(handColor, map(hand[i]), map(hand[j]), stroke, StrokeCap.Round)
                    for (p in hand) drawCircle(Color.White, stroke * 0.9f, map(p))
                    drawText(labels, label, map(hand[0]) + Offset(stroke * 1.5f, stroke), TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
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

// Up to 30 fps: fast signing blurs between frames at 15. Analysis runs only inside a sign window, so heat is bounded.
private const val ANALYSIS_INTERVAL_MS = 30L

/** A sentinel such as Long.MIN_VALUE overflows the subtraction and would skip every frame, so the first one is null. */
internal fun analysisDue(lastMs: Long?, ms: Long) = lastMs == null || ms - lastMs >= ANALYSIS_INTERVAL_MS
// Preview and analysis share one tall 16:9 shape, so the skeleton lines up and a portrait window shows the whole frame.
private val PREVIEW_RESOLUTION = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()

private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

/** The shape of the upright camera frame; windows that show the camera use it so nothing is cropped or letterboxed. */
const val CAMERA_ASPECT = 9f / 16f

package app.signsetu.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.geometry.Offset
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest

private const val MODEL = "models/holistic_landmarker.task"

/** The bundled holistic model on 64-bit phones; 32-bit phones ship without its native library and keep a plain mirror. */
fun holisticTracker(context: Context): LandmarkTracker? =
    if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && context.assets.list("content/models")?.contains("holistic_landmarker.task") == true) {
        HolisticTracker(context.applicationContext)
    } else null

/**
 * MediaPipe Holistic in video mode (D-25), on the GPU first with CPU fallback. The model is mapped from the uncompressed asset, checked against
 * the content manifest and opened by [warm] or the first frame; if the check fails the tracker reports nothing (SEC-54).
 * [close] frees the graph, and the next use opens it again.
 */
class HolisticTracker(private val context: Context) : LandmarkTracker {
    private var lastTimestampMs = Long.MIN_VALUE
    private val lock = Any()
    private var landmarker: HolisticLandmarker? = null
    private var opened = false
    private var delegate = Delegate.GPU

    private fun model(): HolisticLandmarker? = synchronized(lock) {
        if (!opened) {
            landmarker = verifiedModel()?.let { runCatching { open(it) }.getOrNull() }
            opened = true
        }
        landmarker
    }

    override fun warm() {
        model()
    }

    override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame? = synchronized(lock) {
        // Video mode rejects a timestamp that does not move forward.
        if (timestampMs <= lastTimestampMs) return null
        val model = model() ?: return null
        lastTimestampMs = timestampMs
        val r = try {
            model.detectForVideo(BitmapImageBuilder(frame).build(), timestampMs)
        } catch (e: RuntimeException) {
            // A GPU that opened but cannot run the graph: the next frame reopens it on the CPU.
            if (delegate == Delegate.CPU) throw e
            delegate = Delegate.CPU
            landmarker?.close(); landmarker = null; opened = false
            return null
        }
        landmarkFrame(timestampMs, r.leftHandLandmarks(), r.rightHandLandmarks(), r.faceLandmarks(), r.poseLandmarks())
    }

    override fun close() = synchronized(lock) {
        landmarker?.close()
        landmarker = null
        opened = false
        lastTimestampMs = Long.MIN_VALUE
    }

    // The CPU managed about 9 frames a second on a mid-range phone; the GPU is tried first, the CPU when it fails.
    private fun open(model: ByteBuffer): HolisticLandmarker =
        runCatching { open(model, delegate) }.getOrElse { delegate = Delegate.CPU; open(model, Delegate.CPU) }

    private fun open(model: ByteBuffer, delegate: Delegate): HolisticLandmarker = HolisticLandmarker.createFromOptions(
        context,
        HolisticLandmarker.HolisticLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(model).setDelegate(delegate).build())
            .setRunningMode(RunningMode.VIDEO)
            .build(),
    )

    // Mapped, not copied: clean file pages the system can reclaim, instead of 13.7 MB of anonymous memory.
    private fun verifiedModel(): ByteBuffer? = runCatching {
        val manifest = context.assets.open("content/manifest.json").bufferedReader().use { it.readText() }
        val expected = Json.parseToJsonElement(manifest).jsonObject["files"]?.jsonObject?.get(MODEL)?.jsonPrimitive?.content
        val buffer = context.assets.openFd("content/$MODEL").use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length) }
        }
        val actual = MessageDigest.getInstance("SHA-256").apply { update(buffer.duplicate()) }.digest()
            .joinToString("") { "%02x".format(it) }
        buffer.takeIf { expected != null && actual == expected }
    }.getOrNull()
}

/**
 * MediaPipe Holistic assigns hands from the pose, which names the person's own sides, so on an upright unmirrored
 * frame its left hand is the subject's own left hand (seen on the right of the picture). Empty lists mean not seen.
 */
internal fun landmarkFrame(
    timestampMs: Long,
    leftHand: List<NormalizedLandmark>,
    rightHand: List<NormalizedLandmark>,
    face: List<NormalizedLandmark>,
    pose: List<NormalizedLandmark>,
) = LandmarkFrame(timestampMs, leftHand.points(), rightHand.points(), face.points(), pose.points())

private fun List<NormalizedLandmark>.points(): List<Offset>? = takeIf { it.isNotEmpty() }?.map { Offset(it.x(), it.y()) }

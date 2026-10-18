package app.signsetu

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.camera.HolisticTracker
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignReaders
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToLong

/**
 * Dictionary clips of other signers through the real chain: MediaPipe Holistic on the device, sign segmentation and
 * the bundled ISL reader. Tracking and segmentation are asserted; the reader's top three are only reported.
 */
@RunWith(AndroidJUnit4::class)
class SignPipelineDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext

    @Test
    fun dictionaryClipsAreTrackedSegmentedAndRead() {
        val reader = SignReaders.create(app)
        assertNotNull("bundled ISL reader", reader)
        reader!!
        val tracker = HolisticTracker(app)
        val warm = SystemClock.elapsedRealtime().also { tracker.warm() }.let { SystemClock.elapsedRealtime() - it }
        Log.i(TAG, "model opened in $warm ms on ${tracker.delegate}")
        val failures = mutableListOf<String>()
        var trackMs = 0L
        var tracked = 0
        // Timestamps keep rising across clips, as one camera stream would.
        var offset = 0L
        for ((word, desktopHands) in DesktopHandFrames) {
            assertTrue("$word is a reader label", word in reader.labels)
            val frames = ArrayList<LandmarkFrame>()
            decode(word) { ms, bitmap ->
                val t0 = SystemClock.elapsedRealtime()
                val f = tracker.track(bitmap, offset + ms)?.copy(aspect = bitmap.width.toFloat() / bitmap.height)
                trackMs += SystemClock.elapsedRealtime() - t0
                tracked++
                frames += f ?: LandmarkFrame(offset + ms)
            }
            offset = frames.last().timestampMs + 1_000
            val hands = frames.count { it.leftHand != null || it.rightHand != null }
            val pose = frames.count { it.pose != null }
            val capture = SignCapture()
            val segments = frames.flatMap { capture.add(it) } + capture.finish()
            val reads = segments.map { s -> reader.read(s).sortedByDescending { it.score }.take(3) }
            val hit = reads.any { r -> r.any { it.word == word } }
            val seen = frames.joinToString("") { f -> if (f.leftHand != null && f.rightHand != null) "B" else if (f.leftHand != null) "L" else if (f.rightHand != null) "R" else "." }
            Log.i(TAG, "$word: ${frames.size} frames, hands $hands (desktop $desktopHands) $seen, pose $pose, segments ${segments.map { it.size }}, " +
                "top3 ${reads.joinToString(" | ") { r -> r.joinToString { "%s %.2f".format(it.word, it.score) } }}, true word in top3: $hit")
            if (pose * 10 < frames.size * 9) failures += "$word: pose in $pose of ${frames.size} frames"
            if (hands * 10 < desktopHands * 8) failures += "$word: hands in $hands frames, desktop $desktopHands"
            if (segments.isEmpty()) failures += "$word: no sign segmented"
            if (reads.any { it.isEmpty() }) failures += "$word: a segment without guesses"
        }
        Log.i(TAG, "delegate ${tracker.delegate}, ${trackMs / tracked} ms per frame over $tracked frames")
        tracker.close()
        reader.close()
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Every frame of the clip with its time in ms, scaled to the camera analysis size (640 on the long side). */
    private fun decode(word: String, onFrame: (Long, Bitmap) -> Unit) {
        val file = File(app.cacheDir, "$word.mp4")
        instrumentation.context.assets.open("islrtc/$word.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val retriever = MediaMetadataRetriever().apply { setDataSource(file.path) }
        try {
            val count = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val frameMs = durationMs.toDouble() / count
            Log.i(TAG, "$word: $count frames, %.1f fps".format(1000 / frameMs))
            for (i in 0 until count) {
                val full = retriever.getFrameAtIndex(i)!!
                val scale = 640f / maxOf(full.width, full.height)
                val small = if (scale < 1) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
                if (small !== full) full.recycle()
                onFrame((i * frameMs).roundToLong(), small)
                small.recycle()
            }
        } finally {
            retriever.release()
            file.delete()
        }
    }

    private companion object {
        const val TAG = "SignPipelineDeviceTest"
        // Frames with a hand when tools/isl/retarget_islrtc.py ran the same model over each clip on a PC. The signer's
        // hands rest out of view before and after the sign, so most frames have none even there.
        val DesktopHandFrames = mapOf("student" to 25, "today" to 20, "husband" to 39)
    }
}

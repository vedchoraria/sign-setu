package app.signsetu

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.camera.HolisticTracker
import app.signsetu.camera.drawLandmarks
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A real signer's frame through MediaPipe and the camera overlay's own drawing: both hands and the arms must be traced
 * where the person is. The picture is pulled to build/outputs/connected_android_test_additional_output for a person to look at.
 */
@RunWith(AndroidJUnit4::class)
class SkeletonOverlayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext

    @Test
    fun aSignersHandsAndArmsAreTracedOnTheFrame() {
        val file = File(app.cacheDir, "husband.mp4")
        instrumentation.context.assets.open("islrtc/husband.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val retriever = MediaMetadataRetriever().apply { setDataSource(file.path) }
        val count = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
        val full = retriever.getFrameAtIndex(count / 2)!!
        retriever.release()
        file.delete()
        val frame = Bitmap.createScaledBitmap(full, 640, 640 * full.height / full.width, true).copy(Bitmap.Config.ARGB_8888, false)

        val tracker = HolisticTracker(app)
        tracker.warm()
        val seen = tracker.track(frame, 0)?.copy(aspect = frame.width.toFloat() / frame.height)
        tracker.close()
        assertNotNull("a person is found", seen?.pose)
        assertTrue("both hands are found", seen!!.leftHand != null && seen.rightHand != null)
        // The bundled frame that decides whether a phone's GPU can be trusted must itself be read as a person.
        val probe = app.assets.open("content/models/holistic_probe.jpg").use(android.graphics.BitmapFactory::decodeStream)
        val again = HolisticTracker(app)
        assertNotNull("the GPU probe frame shows a person", again.track(probe, 1)?.pose)
        again.close()

        val out = ImageBitmap(frame.width, frame.height)
        val hand = Color(0xFFFFC56B)
        val density = Density(app)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(out), Size(frame.width.toFloat(), frame.height.toFloat())) {
            drawImage(frame.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = IntSize(frame.width, frame.height))
            drawLandmarks(seen, front = false, analysing = true, showFace = false, handColor = hand, labels = TextMeasurer(createFontFamilyResolver(app), density, LayoutDirection.Ltr))
        }
        val bitmap = out.asAndroidBitmap()
        File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: app.cacheDir.path, "overlay.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Hand-coloured bones must sit around the hands, not elsewhere: count them in the box of each traced hand.
        for (h in listOf(seen.leftHand!!, seen.rightHand!!)) {
            val xs = h.map { (it.x * frame.width).toInt().coerceIn(0, frame.width - 1) }
            val ys = h.map { (it.y * frame.height).toInt().coerceIn(0, frame.height - 1) }
            var lit = 0
            for (x in xs.min()..xs.max()) for (y in ys.min()..ys.max()) if (bitmap.getPixel(x, y) == hand.toArgb()) lit++
            assertTrue("bones drawn over the hand ($lit pixels)", lit > 50)
        }
    }
}

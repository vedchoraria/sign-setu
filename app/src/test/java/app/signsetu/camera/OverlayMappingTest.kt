package app.signsetu.camera

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayMappingTest {
    // A 9:16 upright frame letterboxed into a 1000 x 800 window: the picture is 450 wide, from x = 275.
    private val viewW = 1000f
    private val viewH = 800f
    private val wrist = Offset(0.7f, 0.6f)

    private fun near(expected: Offset, actual: Offset) =
        assertTrue("$actual vs $expected", abs(expected.x - actual.x) < 0.03f * viewW && abs(expected.y - actual.y) < 0.03f * viewW)

    @Test fun mirroredLetterboxedWrist() {
        near(Offset(275f + 0.3f * 450f, 0.6f * 800f), overlayPoint(wrist, 9f / 16f, viewW, viewH, mirrored = true))
        near(Offset(275f + 0.7f * 450f, 0.6f * 800f), overlayPoint(wrist, 9f / 16f, viewW, viewH, mirrored = false))
    }

    @Test fun rotatedFrameMatchesUprightOne() {
        val upright = overlayPoint(wrist, 9f / 16f, viewW, viewH, mirrored = true)
        // The same wrist in the sensor's landscape frame, before the clockwise turns that make it upright.
        near(upright, overlayPoint(Offset(wrist.y, 1f - wrist.x), 16f / 9f, viewW, viewH, mirrored = true, rotation = 90))
        near(upright, overlayPoint(Offset(1f - wrist.y, wrist.x), 16f / 9f, viewW, viewH, mirrored = true, rotation = 270))
        near(upright, overlayPoint(Offset(1f - wrist.x, 1f - wrist.y), 9f / 16f, viewW, viewH, mirrored = true, rotation = 180))
    }

    @Test fun wideFrameLetterboxesTopAndBottom() {
        near(Offset(500f, 400f - 1000f * 9f / 32f), overlayPoint(Offset(0.5f, 0f), 16f / 9f, viewW, viewH, mirrored = false))
    }

    /**
     * A recorded INCLUDE clip: the hand model's wrist goes through [overlayPoint], upright and from a sensor buffer, and
     * must land within 3% of the shown frame's width of the hand-labelled wrist pixel on the mirrored, letterboxed preview.
     */
    @Test fun recordedClipWristLandsOnTheLabelledPixel() {
        val rows = javaClass.classLoader!!.getResource("include_hello_wrists.csv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }
        assertEquals(22, rows.size)
        assertEquals(setOf("L", "R"), rows.map { it[1] }.toSet())
        val (fw, fh) = 1920f to 1080f
        for ((w, h) in listOf(1000f to 800f, 720f to 1280f, 400f to 900f)) {
            val scale = minOf(w / fw, h / fh)
            for (r in rows) {
                val (lx, ly, hx, hy) = r.drop(2).map(String::toFloat)
                val label = Offset((w - fw * scale) / 2f + (fw - lx) * scale, (h - fh * scale) / 2f + ly * scale)
                val upright = overlayPoint(Offset(hx / fw, hy / fh), fw / fh, w, h, mirrored = true)
                val sensor = overlayPoint(Offset(1f - hy / fh, hx / fw), fh / fw, w, h, mirrored = true, rotation = 270)
                assertTrue(abs(upright.x - sensor.x) < 0.5f && abs(upright.y - sensor.y) < 0.5f)
                val error = (upright - label).getDistance() / (fw * scale)
                assertTrue("frame ${r[0]} ${r[1]} in $w x $h: $error", error < 0.03f)
            }
        }
    }

    @Test fun handsKeepTheSubjectsSides() {
        // The subject's left hand is on the right of an unmirrored frame, so on the mirror it shows on the left, labelled L.
        val left = List(21) { Offset(0.7f, 0.5f) }
        val right = List(21) { Offset(0.3f, 0.5f) }
        val labelled = labelledHands(LandmarkFrame(0L, leftHand = left, rightHand = right, aspect = 9f / 16f))
        assertEquals(listOf("L" to left, "R" to right), labelled)
        val (l, r) = labelled.map { overlayPoint(it.second[0], 9f / 16f, viewW, viewH, mirrored = true).x }
        assertTrue(l < viewW / 2 && r > viewW / 2)
        assertEquals(listOf("R" to right), labelledHands(LandmarkFrame(0L, leftHand = left.take(5), rightHand = right)))
    }
}

package app.signsetu

import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.HandKey
import app.signsetu.data.content.Key
import app.signsetu.ui.avatar.Side
import app.signsetu.data.content.chain
import app.signsetu.ui.avatar.clampReach
import app.signsetu.ui.avatar.solveElbow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The two-bone arm: reach limits, elbow branch stability, and continuity through a chained sentence. */
class AvatarElbowTest {

    private val upper = AdultBuild.upperArm
    private val fore = AdultBuild.foreArm
    private val shoulder = 100f to 130f

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)

    @Test
    fun aWristInsideTheArmsReachIsLeftWhereItIs() {
        val target = shoulder.first + 20f to shoulder.second + 15f
        assertEquals(target, clampReach(shoulder.first, shoulder.second, target, upper, fore))
    }

    @Test
    fun aWristBeyondTheArmsReachIsPulledBackToArmLength() {
        val far = shoulder.first + 400f to shoulder.second - 300f
        val (x, y) = clampReach(shoulder.first, shoulder.second, far, upper, fore)
        val d = hypot(x - shoulder.first, y - shoulder.second)
        assertEquals(upper + fore - 0.01f, d, 0.02f)
        // Still on the same line of sight: the hand points at where it was asked to go.
        val angle = kotlin.math.atan2((y - shoulder.second).toDouble(), (x - shoulder.first).toDouble())
        val wanted = kotlin.math.atan2((far.second - shoulder.second).toDouble(), (far.first - shoulder.first).toDouble())
        assertEquals(wanted, angle, 1e-4)
    }

    @Test
    fun theElbowSitsOneUpperArmFromTheShoulder() {
        for (target in listOf(40f to 60f, 160f to 60f, 100f to 190f, 250f to 210f)) {
            val elbow = solveElbow(shoulder.first, shoulder.second, target.first, target.second, upper, fore, Side.R, 1f)
            assertEquals("elbow for $target", upper, distance(shoulder, elbow), 0.05f)
        }
    }

    @Test
    fun theElbowKeepsItsBendWhenTheWristCrossesTheShoulderLine() {
        // A hand drawn to the other side of the body passes through the singular point where the two solutions
        // are mirror images. With the previous elbow as the hint the arm turns smoothly; without it, it snaps.
        val targets = (0..24).map { i -> shoulder.first - 60f + i * 5f to shoulder.second + 40f }
        fun elbowDistance(hint: Boolean): Float {
            var previous: Pair<Float, Float>? = null
            var worst = 0f
            for ((tx, ty) in targets) {
                val e = solveElbow(shoulder.first, shoulder.second, tx, ty, upper, fore, Side.R, 1f, if (hint) previous else null)
                if (previous != null) worst = maxOf(worst, distance(previous!!, e))
                previous = e
            }
            return worst
        }
        val smooth = elbowDistance(hint = true)
        val snapping = elbowDistance(hint = false)
        assertTrue("elbow with the hint jumped $smooth", smooth < upper)
        assertTrue("hint should not jump more than the unhinted solve ($smooth vs $snapping)", smooth <= snapping + 0.01f)
    }

    @Test
    fun theElbowNeverSnapsRightAroundTheShoulder() {
        // A hand circling the shoulder, as it does reaching to the far side of the body. The two mirror solves are
        // 50 to 70 units apart here, so a single frame that switches between them is the broken-elbow glitch.
        for (elbowOut in listOf(1f, 0.5f, 0f)) for (radius in listOf(30f, 60f, 90f)) {
            var previous: Pair<Float, Float>? = null
            var worst = 0f
            for (i in 0..720) {
                val a = Math.toRadians(i / 2.0)
                val target = (shoulder.first + radius * cos(a).toFloat()) to (shoulder.second + radius * sin(a).toFloat())
                val e = solveElbow(shoulder.first, shoulder.second, target.first, target.second, upper, fore, Side.R, elbowOut, previous)
                if (previous != null) worst = maxOf(worst, distance(previous!!, e))
                previous = e
            }
            assertTrue("elbowOut=$elbowOut radius=$radius jumped $worst", worst < 5f)
        }
    }

    @Test
    fun aFollowingWordStartsFromTheHandTheLastOneEndedOn() {
        val first = Gesture("one", listOf(Key(0f, R = HandKey(at = "chest", shape = "FLAT")), Key(0.4f, R = HandKey(at = "chest", shape = "FLAT"))))
        val second = Gesture("two", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front", shape = "POINT"))))
        val chained = chain("both", listOf(first, second))
        val end = chained.keys.first { it.t > 0.4f }
        // The second word opens on the first word's hand, not on the rest pose.
        assertEquals(first.keys.last().R, end.R)
        assertNotEquals(HandKey(), end.R)
    }

    @Test
    fun theFirstWordOfASentenceStillStartsFromRest() {
        val only = Gesture("one", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front"))))
        val chained = chain("single", listOf(only))
        assertEquals(HandKey(), chained.keys.first().R)
    }
}

package app.signsetu

import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.HandPose
import app.signsetu.ui.avatar.Pose
import app.signsetu.ui.avatar.Side
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.point
import app.signsetu.ui.avatar.elbowAt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Locale

/** Poses of every gesture at fixed times, which the reviewer portal's TypeScript rig must reproduce
 * (portal/src/rig.test.ts). */
class RigFixtureTest {

    @Serializable private data class GesturesFile(val gestures: List<Gesture>)

    private val content = ASSETS
    private val fixtures = File("../portal/src/rig-fixtures.json")

    @Test
    fun portalFixturesMatchTheRig() {
        val json = Json { ignoreUnknownKeys = true }
        val gestures = listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<GesturesFile>(File(content, it).readText()).gestures } +
            json.decodeFromString<GesturesFile>(File(content, "isl_signs.json").readText()).gestures.take(3) // measured "c:" shapes
        val lines = mutableListOf<String>()
        for (g in gestures) for ((name, build) in listOf("child" to ChildBuild, "adult" to AdultBuild)) {
            val timeline = Timeline(g, build)
            for (frac in floatArrayOf(0.21f, 0.47f, 0.79f)) {
                val s = round4(timeline.duration * frac)
                val pose = timeline.pose(s)
                val values = hand(pose.r, Side.R, build) + hand(pose.l, Side.L, build) + rest(pose)
                lines += """{"gesture":"${g.id}","character":"$name","seconds":${fmt(s)},"pose":[${values.joinToString(",") { it?.let(::fmt) ?: "null" }}]}"""
            }
        }
        val text = "{\"samples\":[\n" + lines.joinToString(",\n") + "\n]}\n"
        if (!fixtures.exists()) {
            fixtures.writeText(text)
            fail("wrote ${fixtures.path}; check rig.test.ts passes against it and run again")
        }
        assertEquals("Rig.kt changed: regenerate ${fixtures.path} (see the class comment)", fixtures.readText(), text)
    }

    private fun hand(h: HandPose, side: Side, build: app.signsetu.ui.avatar.Build): List<Float?> {
        val (sx, sy) = build.shoulder(side)
        val (ex, ey) = h.elbowAt(sx, sy, build, side)
        val (tx, ty) = h.point("index", side, build.handScale)
        val s = h.shape
        return listOf(h.x, h.y, h.roll, h.angle.takeUnless { it.isNaN() }, h.elbowOut,
            s.thumb, s.index, s.middle, s.ring, s.pinky, s.thumbAcross, s.spread, ex, ey, tx, ty)
    }

    private fun rest(p: Pose): List<Float?> = with(p.face) {
        listOf(browRaise, browWorry, browFrown, eyeOpen, eyeSmile, smile, mouthOpen, mouthRound,
            p.tilt, p.nod, p.turn, p.lean, p.shrug, p.hunch, p.shiver)
    }

    private fun round4(v: Float) = fmt(v).toFloat()
    private fun fmt(v: Float) = String.format(Locale.ROOT, "%.4f", v).let { if (it == "-0.0000") "0.0000" else it }
}

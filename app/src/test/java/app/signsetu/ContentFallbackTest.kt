package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.content.SignLanguage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file can match the manifest and still be unreadable; the app must start in its damaged state, not crash. */
class ContentFallbackTest {

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun aMalformedTextFileIsRejectedAndTheBundleStillLoads() {
        val dir = Files.createTempDirectory("signsetu-content").toFile()
        try {
            // The hash is valid, so this failure can only be found by actually parsing the file.
            val malformed = "{ this is not json"
            val cards = """{"categories":[],"cards":[]}"""
            File(dir, "triage.json").writeText(malformed)
            File(dir, "cards.json").writeText(cards)
            File(dir, "manifest.json").writeText(
                """{"files":{"triage.json":"${sha256(malformed)}","cards.json":"${sha256(cards)}"}}""",
            )

            val bundle = runBlocking { ContentRepository { File(dir, it).inputStream() }.load() }

            assertTrue("triage is rejected", "triage.json" in bundle.rejected)
            assertTrue("the subject must not start a session on damaged text", bundle.damaged)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectedAslFilesDisableAslWithoutBlockingIsl() {
        assertTrue(ContentBundle(rejected = setOf(SignLanguage.ASL.signs)).aslDisabled)
        assertTrue(ContentBundle(rejected = setOf(SignLanguage.ASL.words)).aslDisabled)
        assertFalse(ContentBundle(rejected = setOf(SignLanguage.ASL.words)).damaged)
    }

    @Test
    fun anUnreadableManifestReturnsADamagedBundleInsteadOfThrowing() {
        val dir = Files.createTempDirectory("signsetu-manifest").toFile()
        try {
            File(dir, "manifest.json").writeText("not json")

            val bundle = runBlocking { ContentRepository { File(dir, it).inputStream() }.load() }

            assertTrue("the manifest is the rejected file", "manifest.json" in bundle.rejected)
            assertTrue(bundle.damaged)
        } finally {
            dir.deleteRecursively()
        }
    }
}

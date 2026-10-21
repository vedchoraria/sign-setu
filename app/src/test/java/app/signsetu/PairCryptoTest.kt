package app.signsetu

import app.signsetu.pair.Identity
import app.signsetu.pair.LinkSession
import app.signsetu.pair.PairAbort
import app.signsetu.pair.Abort
import app.signsetu.pair.officerHandshake
import app.signsetu.pair.stageHandshake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class PairCryptoTest {

    private val idOfficer = Identity(versionCode = 2, contentHash = "hash123", model = "Pixel 6")
    private val idStage = Identity(versionCode = 2, contentHash = "hash123", model = "Samsung Tab")

    @Test
    fun completeHandshakeAndEncryptedSessionExchange() = runTest {
        // Connect officer OUT -> stage IN
        val officerOut = PipedOutputStream()
        val stageIn = PipedInputStream(officerOut)

        // Connect stage OUT -> officer IN
        val stageOut = PipedOutputStream()
        val officerIn = PipedInputStream(stageOut)

        val officerTask = async(Dispatchers.IO) {
            officerHandshake(DataInputStream(officerIn), DataOutputStream(officerOut), idOfficer, resumeKey = null)
        }
        val stageTask = async(Dispatchers.IO) {
            stageHandshake(DataInputStream(stageIn), DataOutputStream(stageOut), idStage, resumeKey = null)
        }

        val officerKeys = officerTask.await()
        val stageKeys = stageTask.await()

        // Verify that 6-digit confirmation code and 4-digit check code match exactly on both devices
        assertEquals(6, officerKeys.code.length)
        assertEquals(officerKeys.code, stageKeys.code)
        assertEquals(4, officerKeys.check.length)
        assertEquals(officerKeys.check, stageKeys.check)
        assertNotNull(officerKeys.resumeKey)
        assertNotNull(stageKeys.resumeKey)

        // Verify LinkSession encrypted bidirectional communication
        val officerSession = LinkSession(DataInputStream(officerIn), DataOutputStream(officerOut), officerKeys, officer = true)
        val stageSession = LinkSession(DataInputStream(stageIn), DataOutputStream(stageOut), stageKeys, officer = false)

        val officerPayload = "SHOW:card:water".toByteArray()
        val sendTask = async(Dispatchers.IO) {
            officerSession.send(officerPayload)
        }
        val recvTask = async(Dispatchers.IO) {
            stageSession.receive()
        }
        sendTask.await()
        val receivedOnStage = recvTask.await()
        assertArrayEquals(officerPayload, receivedOnStage)

        val stagePayload = "ANSWER:card:water".toByteArray()
        val stageSendTask = async(Dispatchers.IO) {
            stageSession.send(stagePayload)
        }
        val officerRecvTask = async(Dispatchers.IO) {
            officerSession.receive()
        }
        stageSendTask.await()
        val receivedOnOfficer = officerRecvTask.await()
        assertArrayEquals(stagePayload, receivedOnOfficer)
    }

    @Test
    fun versionMismatchAbortsHandshake() = runTest {
        val officerOut = PipedOutputStream()
        val stageIn = PipedInputStream(officerOut)
        val stageOut = PipedOutputStream()
        val officerIn = PipedInputStream(stageOut)

        val mismatchedStage = Identity(versionCode = 3, contentHash = "hash123", model = "Samsung Tab")

        val officerTask = async(Dispatchers.IO) {
            runCatching {
                officerHandshake(DataInputStream(officerIn), DataOutputStream(officerOut), idOfficer, resumeKey = null)
            }
        }
        val stageTask = async(Dispatchers.IO) {
            runCatching {
                stageHandshake(DataInputStream(stageIn), DataOutputStream(stageOut), mismatchedStage, resumeKey = null)
            }
        }

        val officerResult = officerTask.await()
        val stageResult = stageTask.await()

        // At least one side must abort with Abort.VERSION
        val aborted = (officerResult.exceptionOrNull() as? PairAbort)?.reason == Abort.VERSION ||
            (stageResult.exceptionOrNull() as? PairAbort)?.reason == Abort.VERSION
        assert(aborted)
    }
}

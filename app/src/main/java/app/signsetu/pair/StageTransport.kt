package app.signsetu.pair

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** A device the officer can pick: [id] is what the transport connects to, [label] what the officer reads. */
data class Peer(val id: String, val label: String)

/** One duplex byte stream to [peer], a stable id the link pins for resume. */
class Pipe(val input: InputStream, val output: OutputStream, val peer: String, private val onClose: () -> Unit) : Closeable {
    override fun close() = onClose()
}

/** The byte transport under a link, kept free of Android types so the protocol runs in JVM tests over an in-
 * process fake (the shape of Ping's NearbyTransport). */
interface StageTransport {
    /** Devices the officer may connect to; bonded devices for Bluetooth, discovered ones for Nearby. */
    fun peers(): Flow<List<Peer>>

    /** Officer side; blocks until connected or throws. Cancelling closes the attempt. */
    suspend fun connect(peer: String): Pipe

    /** Stage side; waits for one officer. [onlyPeer] restricts a resume to the pinned device. */
    suspend fun accept(onlyPeer: String?): Pipe

    /** This device's own name for the stage's waiting screen; shown locally, never sent. */
    fun localName(): String? = null
}

/** Runs blocking I/O on the IO dispatcher; cancellation (a timeout too) calls [close], since platform sockets
 * ignore coroutine cancellation and thread interrupts. */
suspend fun <T> closing(close: () -> Unit, block: () -> T): T = coroutineScope {
    var finished = false
    val watcher = launch { try { awaitCancellation() } finally { if (!finished) close() } }
    try {
        withContext(Dispatchers.IO) { block() }.also { finished = true }
    } finally {
        watcher.cancel()
    }
}

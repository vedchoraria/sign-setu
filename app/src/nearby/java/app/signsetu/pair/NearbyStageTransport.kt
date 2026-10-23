package app.signsetu.pair

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Google Nearby Connections under the same link (nearby flavor only; the shape of Ping's
 * NearbyConnectionsTransport). */
class NearbyStageTransport(context: Context) : StageTransport {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val name = "Display-" + ByteArray(2).also(SecureRandom()::nextBytes).toHexString(HexFormat.UpperCase)
    private val found = MutableStateFlow<Map<String, String>>(emptyMap())
    private val links = ConcurrentHashMap<String, Link>()
    private val names = ConcurrentHashMap<String, String>()
    @Volatile private var waiter: CompletableDeferred<Pipe>? = null
    @Volatile private var onlyPeer: String? = null
    @Volatile private var dialing = false
    private val options = Strategy.P2P_POINT_TO_POINT

    private inner class Link(val endpointId: String, val peerName: String) {
        val feed = PipedOutputStream()
        val input = PipedInputStream(feed, 2 shl 20)
        val output = object : OutputStream() {
            private val buffer = ByteArrayOutputStream()
            override fun write(b: Int) = buffer.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
            override fun flush() {
                val bytes = buffer.toByteArray()
                buffer.reset()
                var i = 0
                while (i < bytes.size) {
                    val end = minOf(bytes.size, i + ConnectionsClient.MAX_BYTES_DATA_SIZE)
                    client.sendPayload(endpointId, Payload.fromBytes(bytes.copyOfRange(i, end)))
                    i = end
                }
            }
        }

        fun close() {
            links.remove(endpointId)
            runCatching { feed.close() }
            client.disconnectFromEndpoint(endpointId)
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            links[endpointId]?.let { runCatching { it.feed.write(bytes); it.feed.flush() } }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val only = onlyPeer
            if (waiter != null && (only == null || info.endpointName == only)) {
                names[endpointId] = info.endpointName
                client.acceptConnection(endpointId, payloads)
            } else {
                client.rejectConnection(endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peerName = names.remove(endpointId) ?: return
            val w = waiter
            when {
                result.status.isSuccess && w != null && !w.isCompleted -> {
                    val link = Link(endpointId, peerName)
                    links[endpointId] = link
                    w.complete(Pipe(link.input, link.output, peerName, link::close))
                }
                result.status.isSuccess -> client.disconnectFromEndpoint(endpointId)
                dialing -> w?.completeExceptionally(IOException("rejected"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            links.remove(endpointId)?.let { runCatching { it.feed.close() } }
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName.startsWith("Display-")) found.update { it + (endpointId to info.endpointName) }
        }

        override fun onEndpointLost(endpointId: String) = found.update { it - endpointId }
    }

    private fun startDiscovery() {
        client.startDiscovery(SERVICE, discovery, DiscoveryOptions.Builder().setStrategy(options).build())
    }

    override fun peers(): Flow<List<Peer>> = flow {
        startDiscovery()
        try {
            emitAll(found.map { m -> m.values.distinct().sorted().map { Peer(it, it) } })
        } finally {
            client.stopDiscovery()
        }
    }

    override suspend fun connect(peer: String): Pipe {
        val w = CompletableDeferred<Pipe>()
        waiter = w
        onlyPeer = peer
        dialing = true
        startDiscovery()
        try {
            val endpointId = found.first { m -> peer in m.values }.entries.first { it.value == peer }.key
            client.requestConnection("Phone-" + name.takeLast(4), endpointId, lifecycle)
                .addOnFailureListener { w.completeExceptionally(IOException("request")) }
            return w.await()
        } finally {
            waiter = null
            dialing = false
            client.stopDiscovery()
        }
    }

    override suspend fun accept(onlyPeer: String?): Pipe {
        val w = CompletableDeferred<Pipe>()
        waiter = w
        this.onlyPeer = onlyPeer
        client.startAdvertising(name, SERVICE, lifecycle, AdvertisingOptions.Builder().setStrategy(options).build())
            .addOnFailureListener { w.completeExceptionally(IOException("advertise")) }
        try {
            return w.await()
        } finally {
            waiter = null
            client.stopAdvertising()
        }
    }

    override fun localName(): String = name

    private companion object {
        const val SERVICE = "app.signsetu.display"
    }
}

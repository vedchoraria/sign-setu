package app.signsetu.pair

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Secure RFCOMM to devices already paired in system settings. The app never scans, never makes itself
 * discoverable and never bonds, so it needs only BLUETOOTH_CONNECT (BLUETOOTH up to Android 11).
 * Callers hold the runtime permission before any call; the stage is the server, the officer the client.
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(val adapter: BluetoothAdapter) : StageTransport {

    override fun peers(): Flow<List<Peer>> = flow { emit(bonded()) }

    /** Phones first; the address tail tells apart department phones of one model. */
    private fun bonded(): List<Peer> = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        .filter { it.bluetoothClass?.majorDeviceClass in ScreenClasses }
        .sortedBy { if (it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE) 0 else 1 }
        .map { Peer(it.address, "${it.name ?: it.address} ${it.address.takeLast(5)}") }

    override suspend fun connect(peer: String): Pipe {
        val socket = adapter.getRemoteDevice(peer).createRfcommSocketToServiceRecord(STAGE_UUID)
        closing({ runCatching { socket.close() } }) { socket.connect() }
        return Pipe(socket.inputStream, socket.outputStream, peer) { runCatching { socket.close() } }
    }

    override suspend fun accept(onlyPeer: String?): Pipe {
        while (true) {
            // A generic service name: the SDP record must not advertise a police device.
            val server = adapter.listenUsingRfcommWithServiceRecord("Display", STAGE_UUID)
            val socket = try {
                closing({ runCatching { server.close() } }) { server.accept() }
            } finally {
                // One client per channel; nothing else may connect while this link lives.
                runCatching { server.close() }
            }
            val device = socket.remoteDevice
            if (device.bondState == BluetoothDevice.BOND_BONDED && (onlyPeer == null || device.address == onlyPeer)) {
                return Pipe(socket.inputStream, socket.outputStream, device.address) { runCatching { socket.close() } }
            }
            runCatching { socket.close() }
        }
    }

    override fun localName(): String? = runCatching { adapter.name }.getOrNull()

    companion object {
        /** Fixed and app-specific; never the serial-port UUID. */
        val STAGE_UUID: UUID = UUID.fromString("6f1d2c4e-93b8-4b57-a6e2-2d0c8f5e7a41")
        private val ScreenClasses = setOf(BluetoothClass.Device.Major.PHONE, BluetoothClass.Device.Major.COMPUTER, BluetoothClass.Device.Major.UNCATEGORIZED)

        fun create(context: Context): BluetoothTransport? = context.getSystemService(BluetoothManager::class.java)?.adapter?.let(::BluetoothTransport)

        fun permissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    }
}

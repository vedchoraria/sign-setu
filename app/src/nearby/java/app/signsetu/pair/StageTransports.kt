package app.signsetu.pair

import android.Manifest
import android.content.Context
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** The nearby build: Nearby Connections where Play services exist, bonded Bluetooth elsewhere. */
object StageTransports {
    private fun playServices(context: Context) =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun create(context: Context): StageTransport? =
        if (playServices(context)) NearbyStageTransport(context.applicationContext) else BluetoothTransport.create(context)

    fun permissions(context: Context): Array<String> = when {
        !playServices(context) -> BluetoothTransport.permissions()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

package app.signsetu.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

class Reply(val code: Int, val body: String) {
    val ok get() = code in 200..299
}

/** One blocking HTTPS call; null when the network or server could not be reached. Faked in tests. */
fun interface Http {
    fun call(method: String, url: String, body: String?, bearer: String?): Reply?
}

/** Platform HTTP over TLS. Cleartext is refused by the network security config and again here. */
class UrlHttp : Http {
    override fun call(method: String, url: String, body: String?, bearer: String?): Reply? {
        if (!url.startsWith("https://")) return null
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) doOutput = true
        }
        return try {
            body?.let { conn.outputStream.use { o -> o.write(it.toByteArray()) } }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            Reply(conn.responseCode, stream?.use { it.readBytes().decodeToString() }.orEmpty())
        } catch (e: java.io.IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

/** A network with working internet, not just a connected one: captive portals fail this. */
fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

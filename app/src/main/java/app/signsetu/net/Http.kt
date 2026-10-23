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
    /** [timeoutMs] bounds the wait for the reply; a short one keeps a dead gateway from holding up enrolment. */
    fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply?

    /** The chat/report timeout; a full report from the free gateway model takes 30 to 45 s. */
    fun call(method: String, url: String, body: String?, bearer: String?): Reply? =
        call(method, url, body, bearer, 60_000)
}

/** Platform HTTP over TLS. Cleartext is refused by the network security config and again here. */
class UrlHttp : Http {
    override fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply? {
        if (!url.startsWith("https://")) return null
        val bytes = body?.toByteArray()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(10_000, timeoutMs)
            // A full report from the free gateway model takes 30 to 45 s; the officer waits behind a progress dialog.
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (bytes != null) {
                doOutput = true
                // A declared length lets the gateway refuse an oversized body before reading it, and avoids chunked uploads.
                setFixedLengthStreamingMode(bytes.size)
            }
        }
        return try {
            bytes?.let { conn.outputStream.use { o -> o.write(it) } }
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

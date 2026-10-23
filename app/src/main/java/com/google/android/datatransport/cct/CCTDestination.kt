package com.google.android.datatransport.cct

import com.google.android.datatransport.Encoding
import com.google.android.datatransport.runtime.EncodedDestination

/** Stands in for the excluded upload backend that MediaPipe's task runner names. No backend is registered for
 * it, so the transport runtime drops every statistics event on the device and nothing is ever sent. */
object CCTDestination : EncodedDestination {
    override fun getName() = "none"
    override fun getExtras(): ByteArray? = null
    override fun getSupportedEncodings(): Set<Encoding> = setOf(Encoding.of("proto"))
}

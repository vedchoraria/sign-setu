package app.signsetu.data.session

import java.security.MessageDigest

/** The hash chain over a session's log, sealed as each row is written: every row's hash covers the previous row's
 * hash, its own fields and the bytes of its drawing, so an edited, inserted or removed row breaks every later hash. */
object EventSeal {
    fun genesis(sessionId: String, startedAt: Long): String = sha256Hex("signsetu-chain-v1" + field(sessionId) + field(startedAt.toString()))

    fun seal(
        previous: String,
        sessionId: String,
        ts: Long,
        type: String,
        source: String,
        lang: String?,
        text: String?,
        cardId: String?,
        lexiconId: String?,
        drawing: ByteArray?,
        itemId: String?,
        reason: String?,
        shownId: Long?,
        via: String?,
    ): String = sha256Hex(
        previous + field(sessionId) + field(ts.toString()) + field(type) + field(source) + field(lang) + field(text) +
            field(cardId) + field(lexiconId) + field(drawing?.let(::sha256Hex)) + field(itemId) + field(reason) +
            field(shownId?.toString()) + field(via),
    )

    // Length-prefixed, with null apart from empty, so no two different rows encode the same.
    private fun field(value: String?) = if (value == null) "|-" else "|${value.length}:$value"

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
}

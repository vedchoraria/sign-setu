package app.signsetu.pair

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Every way a link is refused; the reason is shown to the officer, never any peer-sent text (SEC-67). */
enum class Abort { PROTOCOL, VERSION, MODE, COMMIT, KEY, SIZE, CODE }

class PairAbort(val reason: Abort) : IOException(reason.name)

const val PLAIN_CAP = 4 * 1024
const val FRAME_CAP = 1 shl 20
private const val MAX_FRAMES = 1L shl 32

/** RFC 5869 over HmacSHA256; the same construction as Ping's CryptoUtils, with any output length. */
object Hkdf {
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = mac(salt).doFinal(ikm)

    fun expand(prk: ByteArray, info: String, length: Int): ByteArray {
        val mac = mac(prk)
        val out = java.io.ByteArrayOutputStream()
        var block = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            mac.update(block); mac.update(info.toByteArray()); mac.update(i++.toByte())
            block = mac.doFinal()
            out.write(block)
        }
        return out.toByteArray().copyOf(length)
    }

    private fun mac(key: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
}

fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run { parts.forEach(::update); digest() }

private val b64 = Base64.getEncoder()
private val unb64 = Base64.getDecoder()
internal fun ByteArray.b64(): String = b64.encodeToString(this)
internal fun String.unb64(): ByteArray = try { unb64.decode(this) } catch (_: IllegalArgumentException) { throw PairAbort(Abort.PROTOCOL) }

/** What each side states about itself; both must run the same build and the same content (SEC-67). */
data class Identity(val versionCode: Int, val contentHash: String, val model: String)

@Serializable internal data class Hello1(val v: Int, val versionCode: Int, val contentHash: String, val model: String, val mode: String, val commit: String)
@Serializable internal data class Hello2(val v: Int, val versionCode: Int, val contentHash: String, val model: String, val mode: String, val pk: String, val n: String)
@Serializable internal data class Reveal(val pk: String, val n: String)

private val handshakeJson = Json { ignoreUnknownKeys = false }
const val MODE_NEW = "new"
const val MODE_RESUME = "resume"

/**
 * Keys for one link; [code] is the 6 digits, [resumeKey] is set on new links only. [check] is shown on both screens
 * once the officer's phone has accepted the code, so the stage goes live only for the phone in the officer's hand.
 */
class LinkKeys(val send: ByteArray, val receive: ByteArray, val code: String, val resumeKey: ByteArray?, val peerModel: String, val check: String)

private val P256 by lazy {
    (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey).params
}

/** An X.509 key that is a point on P-256, or an abort; the platform does not check the curve for us everywhere. */
internal fun decodeP256(bytes: ByteArray): ECPublicKey {
    val key = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes)) }.getOrNull() as? ECPublicKey
        ?: throw PairAbort(Abort.KEY)
    val p = P256
    if (key.params.curve != p.curve || key.params.generator != p.generator || key.params.order != p.order) throw PairAbort(Abort.KEY)
    val prime = (p.curve.field as java.security.spec.ECFieldFp).p
    val x = key.w.affineX
    val y = key.w.affineY
    if (x.signum() < 0 || y.signum() < 0 || x >= prime || y >= prime) throw PairAbort(Abort.KEY)
    val rhs = x.pow(3).add(p.curve.a.multiply(x)).add(p.curve.b).mod(prime)
    if (y.multiply(y).mod(prime) != rhs) throw PairAbort(Abort.KEY)
    return key
}

internal fun writePlain(out: DataOutputStream, bytes: ByteArray) {
    out.writeInt(bytes.size); out.write(bytes); out.flush()
}

internal fun readPlain(input: DataInputStream): ByteArray {
    val len = input.readInt()
    if (len < 0 || len > PLAIN_CAP) throw PairAbort(Abort.SIZE)
    return ByteArray(len).also(input::readFully)
}

private inline fun <reified T> decode(bytes: ByteArray): T =
    try { handshakeJson.decodeFromString<T>(bytes.decodeToString()) } catch (_: Exception) { throw PairAbort(Abort.PROTOCOL) }

internal fun derive(ss: ByteArray, transcript: ByteArray, resumeKey: ByteArray?, officer: Boolean, peerModel: String): LinkKeys {
    val salt = if (resumeKey == null) transcript else sha256(resumeKey, transcript)
    val prk = Hkdf.extract(salt, ss)
    ss.fill(0)
    val o2s = Hkdf.expand(prk, "signsetu stage v1 o2s", 32)
    val s2o = Hkdf.expand(prk, "signsetu stage v1 s2o", 32)
    val codeBytes = Hkdf.expand(prk, "signsetu stage v1 code", 4)
    val newResume = if (resumeKey == null) Hkdf.expand(prk, "signsetu stage v1 resume", 32) else null
    val checkBytes = Hkdf.expand(prk, "signsetu stage v1 confirm", 4)
    prk.fill(0)
    fun digits(bytes: ByteArray, mod: Long) = bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) } % mod
    val code = "%06d".format(digits(codeBytes, 1_000_000L))
    val check = "%04d".format(digits(checkBytes, 10_000L))
    return if (officer) LinkKeys(o2s, s2o, code, newResume, peerModel, check) else LinkKeys(s2o, o2s, code, newResume, peerModel, check)
}

private fun ecdh(private: java.security.PrivateKey, peer: ECPublicKey): ByteArray =
    KeyAgreement.getInstance("ECDH").run { init(private); doPhase(peer, true); generateSecret() }

private fun newKeyPair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

private fun checkPeer(me: Identity, versionCode: Int, contentHash: String) {
    if (versionCode != me.versionCode || contentHash != me.contentHash) throw PairAbort(Abort.VERSION)
}

/**
 * Officer side of the commit-then-reveal handshake: the officer commits to its key before seeing the stage's, so
 * neither side can search for a key that yields a chosen code (SEC-67). [resumeKey] non-null means a resume.
 */
fun officerHandshake(input: DataInputStream, out: DataOutputStream, me: Identity, resumeKey: ByteArray?): LinkKeys {
    val mode = if (resumeKey == null) MODE_NEW else MODE_RESUME
    val kp = newKeyPair()
    val nonce = ByteArray(32).also(SecureRandom()::nextBytes)
    val pk = kp.public.encoded
    val h1 = handshakeJson.encodeToString(Hello1.serializer(), Hello1(1, me.versionCode, me.contentHash, me.model, mode, sha256(pk, nonce).b64())).toByteArray()
    writePlain(out, h1)
    val h2bytes = readPlain(input)
    val h2 = decode<Hello2>(h2bytes)
    if (h2.v != 1) throw PairAbort(Abort.PROTOCOL)
    checkPeer(me, h2.versionCode, h2.contentHash)
    if (h2.mode != mode) throw PairAbort(Abort.MODE)
    val peer = decodeP256(h2.pk.unb64())
    if (h2.n.unb64().size != 32) throw PairAbort(Abort.PROTOCOL)
    val reveal = handshakeJson.encodeToString(Reveal.serializer(), Reveal(pk.b64(), nonce.b64())).toByteArray()
    writePlain(out, reveal)
    nonce.fill(0)
    return derive(ecdh(kp.private, peer), sha256(h1, h2bytes, reveal), resumeKey, officer = true, peerModel = h2.model.take(80))
}

/** Stage side; it answers in the mode its own state expects, so a resume can never be upgraded to a new pairing. */
fun stageHandshake(input: DataInputStream, out: DataOutputStream, me: Identity, resumeKey: ByteArray?): LinkKeys {
    val mode = if (resumeKey == null) MODE_NEW else MODE_RESUME
    val h1bytes = readPlain(input)
    val h1 = decode<Hello1>(h1bytes)
    if (h1.v != 1) throw PairAbort(Abort.PROTOCOL)
    val kp = newKeyPair()
    val nonce = ByteArray(32).also(SecureRandom()::nextBytes)
    // Hello2 goes out before the checks below, so the officer learns of a version mismatch too.
    val h2 = handshakeJson.encodeToString(Hello2.serializer(), Hello2(1, me.versionCode, me.contentHash, me.model, mode, kp.public.encoded.b64(), nonce.b64())).toByteArray()
    writePlain(out, h2)
    nonce.fill(0)
    checkPeer(me, h1.versionCode, h1.contentHash)
    if (h1.mode != mode) throw PairAbort(Abort.MODE)
    val revealBytes = readPlain(input)
    val reveal = decode<Reveal>(revealBytes)
    val pk = reveal.pk.unb64()
    val n = reveal.n.unb64()
    if (n.size != 32 || !MessageDigest.isEqual(sha256(pk, n), h1.commit.unb64())) throw PairAbort(Abort.COMMIT)
    val peer = decodeP256(pk)
    return derive(ecdh(kp.private, peer), sha256(h1bytes, h2, revealBytes), resumeKey, officer = false, peerModel = h1.model.take(80))
}

/**
 * Encrypted frames: u32 length | u64 counter | AES-256-GCM ciphertext and tag. The IV is direction and counter, so
 * no IV repeats under a key, and the receiver accepts only the next counter, so replays and gaps close the link.
 */
class LinkSession(private val input: DataInputStream, private val out: DataOutputStream, keys: LinkKeys, officer: Boolean) {
    private val sendDir: Byte = if (officer) 1 else 2
    private val recvDir: Byte = if (officer) 2 else 1
    private var sendKey: SecretKeySpec? = SecretKeySpec(keys.send, "AES")
    private var recvKey: SecretKeySpec? = SecretKeySpec(keys.receive, "AES")
    private val enc = Cipher.getInstance("AES/GCM/NoPadding")
    private val dec = Cipher.getInstance("AES/GCM/NoPadding")
    private val lock = Any()
    private var sent = 0L
    private var expected = 0L

    init {
        keys.send.fill(0)
        keys.receive.fill(0)
    }

    fun send(plain: ByteArray) = synchronized(lock) {
        val key = sendKey ?: throw IOException("closed")
        if (sent >= MAX_FRAMES) throw IOException("counter")
        val counter = sent++
        enc.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv(sendDir, counter)))
        enc.updateAAD(aad(counter))
        val ct = enc.doFinal(plain)
        out.writeInt(ct.size); out.writeLong(counter); out.write(ct); out.flush()
    }

    /** Blocks for the next frame; any fault throws and the caller closes the link. */
    fun receive(): ByteArray {
        val len = input.readInt()
        if (len < 16 || len > FRAME_CAP) throw PairAbort(Abort.SIZE)
        val counter = input.readLong()
        if (counter != expected) throw PairAbort(Abort.PROTOCOL)
        val ct = ByteArray(len).also(input::readFully)
        val key = recvKey ?: throw IOException("closed")
        dec.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv(recvDir, counter)))
        dec.updateAAD(aad(counter))
        return dec.doFinal(ct).also { expected++ }
    }

    /** Copies inside SecretKeySpec and the provider are out of reach; process exit is the real wipe (SEC-69). */
    fun close() {
        sendKey = null
        recvKey = null
    }

    private fun iv(dir: Byte, counter: Long) = java.nio.ByteBuffer.allocate(12).put(dir).put(0).put(0).put(0).putLong(counter).array()
    private fun aad(counter: Long) = java.nio.ByteBuffer.allocate(9).put(1).putLong(counter).array()
}

/** Constant-time check of the 6 digits the officer typed (SEC-67). */
fun codeMatches(typed: String, code: String): Boolean = MessageDigest.isEqual(typed.trim().toByteArray(Charsets.US_ASCII), code.toByteArray(Charsets.US_ASCII))

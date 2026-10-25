package initiative.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What two Initiatives say to each other over a Bluetooth stream, kept apart
 * from Bluetooth so it can be tested with plain pipes.
 *
 * Pairing: a fresh key agreement (elliptic-curve Diffie-Hellman, P-256), with
 * the connecting phone committing to its key first, so a third phone in the
 * middle can't search for keys that give both people the same code. Both
 * screens then show a 6-digit code made from the exchange; matching codes mean
 * no one is in the middle. Two keys come out: one to prove who's who, one to
 * seal what's shared.
 *
 * Ping: each side proves which pair it is, without naming itself, then each
 * sends what it shares with the other, sealed (AES-GCM).
 */
object PairWire {
    const val VERSION = 1
    private const val MAX_FRAME = 512 * 1024
    private val random = SecureRandom()

    /** One message: its length, then its bytes. */
    fun write(out: OutputStream, bytes: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(bytes.size)
        d.write(bytes)
        d.flush()
    }

    fun read(input: InputStream): ByteArray {
        val d = DataInputStream(input)
        val size = d.readInt()
        require(size in 0..MAX_FRAME) { "frame too large" }
        return ByteArray(size).also { d.readFully(it) }
    }

    /** What pairing agrees: the other phone's ID, the code both screens show, and the pair's two keys. */
    class Agreed(val theirId: String, val code: String, val macKey: ByteArray, val sealKey: ByteArray)

    /**
     * Pairing's key agreement. [initiator]: this phone made the connection.
     * Throws when the other side breaks the protocol or speaks a newer version.
     */
    fun agree(input: InputStream, out: OutputStream, myId: String, initiator: Boolean): Agreed {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val mine = gen.generateKeyPair()
        val myPub = mine.public.encoded
        val myNonce = nonce()
        val myHello = hello(myId, myPub, myNonce)
        val theirHello: ByteArray
        if (initiator) {
            // Commit first, reveal after seeing theirs.
            write(out, sha256(myHello))
            theirHello = read(input)
            write(out, myHello)
        } else {
            val commitment = read(input)
            write(out, myHello)
            theirHello = read(input)
            require(MessageDigest.isEqual(commitment, sha256(theirHello))) { "commitment broken" }
        }
        val (theirId, theirPub, theirNonce) = parseHello(theirHello)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(mine.private)
        agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(theirPub)), true)
        val secret = agreement.generateSecret()
        // Both sides order the exchange the same way: the initiator's half first.
        val transcript = if (initiator) myHello + theirHello else theirHello + myHello
        val okm = hkdf(secret, sha256(transcript), "initiative pair".toByteArray(), 68)
        val n = ((okm[64].toInt() and 0xFF) shl 24 or ((okm[65].toInt() and 0xFF) shl 16) or ((okm[66].toInt() and 0xFF) shl 8) or (okm[67].toInt() and 0xFF))
        val code = "%06d".format(Integer.toUnsignedLong(n) % 1_000_000L)
        return Agreed(theirId, code, okm.copyOfRange(0, 32), okm.copyOfRange(32, 64))
            .also { require(theirNonce.size == NONCE && myNonce.size == NONCE) }
    }

    /** After both people looked at the code: each says yes or no; true only when both said yes. */
    fun confirm(input: InputStream, out: OutputStream, yes: Boolean): Boolean {
        write(out, byteArrayOf(if (yes) 1 else 0))
        return yes && read(input).contentEquals(byteArrayOf(1))
    }

    /** One paired person's keys, as kept on this phone; [peer] is this phone's own row for them. */
    class PairKeys(val peer: Long, val mac: SecretKey, val seal: SecretKey)

    /** What a ping brought: which pair it was, and what they share, as text. */
    class Pinged(val peer: Long, val text: String)

    /**
     * A ping, once connected: prove which pair this is, then swap what each
     * shares. [outgoing] gives this phone's text for the matched person. Null
     * when the other phone is no one paired with this one.
     */
    fun ping(input: InputStream, out: OutputStream, keys: List<PairKeys>, initiator: Boolean, outgoing: (Long) -> String): Pinged? {
        val myNonce = nonce()
        write(out, byteArrayOf(VERSION.toByte()) + myNonce)
        val theirs = read(input)
        require(theirs.size == 1 + NONCE && theirs[0].toInt() <= VERSION) { "unknown version" }
        val theirNonce = theirs.copyOfRange(1, theirs.size)
        val session = if (initiator) myNonce + theirNonce else theirNonce + myNonce
        // A proof for each of this phone's pairs; the matching one names the pair to both sides alone.
        val proofs = keys.map { it to hmac(it.mac, "who".toByteArray() + session) }
        write(out, proofs.fold(ByteArray(0)) { acc, (_, p) -> acc + p })
        val theirProofs = read(input).toList().chunked(PROOF).map { it.toByteArray() }
        val match = proofs.firstOrNull { (_, p) -> theirProofs.any { MessageDigest.isEqual(it, p) } }?.first ?: return null
        val me = if (initiator) "A" else "B"
        val them = if (initiator) "B" else "A"
        write(out, seal(match.seal, outgoing(match.peer).toByteArray(), session + me.toByteArray()))
        val text = open(match.seal, read(input), session + them.toByteArray()).decodeToString()
        return Pinged(match.peer, text)
    }

    fun hmac(key: SecretKey, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data)

    /** Sealed: the nonce the cipher chose, then the ciphertext. */
    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(aad)
        val body = c.doFinal(plain)
        return byteArrayOf(c.iv.size.toByte()) + c.iv + body
    }

    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray {
        val ivSize = sealed[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 1, ivSize))
        c.updateAAD(aad)
        return c.doFinal(sealed, 1 + ivSize, sealed.size - 1 - ivSize)
    }

    /** Raw key bytes as keys, for tests and for import into the phone's key store. */
    fun macKey(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "HmacSHA256")
    fun sealKey(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    private fun hello(id: String, pub: ByteArray, nonce: ByteArray): ByteArray {
        val idBytes = id.toByteArray()
        require(idBytes.size < 256 && pub.size < 65536)
        return byteArrayOf(VERSION.toByte(), idBytes.size.toByte()) + idBytes +
            byteArrayOf((pub.size shr 8).toByte(), pub.size.toByte()) + pub + nonce
    }

    private fun parseHello(b: ByteArray): Triple<String, ByteArray, ByteArray> {
        require(b[0].toInt() in 1..VERSION) { "unknown version" }
        val idLen = b[1].toInt() and 0xFF
        val id = b.copyOfRange(2, 2 + idLen).decodeToString()
        var i = 2 + idLen
        val pubLen = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        i += 2
        val pub = b.copyOfRange(i, i + pubLen)
        val n = b.copyOfRange(i + pubLen, b.size)
        require(n.size == NONCE) { "bad hello" }
        return Triple(id, pub, n)
    }

    private fun nonce() = ByteArray(NONCE).also(random::nextBytes)

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    /** HKDF with SHA-256 (RFC 5869). */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(ikm)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        var t = ByteArray(0)
        var out = ByteArray(0)
        var i = 1
        while (out.size < length) {
            t = mac.doFinal(t + info + byteArrayOf(i.toByte()))
            out += t
            i++
        }
        return out.copyOf(length)
    }

    private const val NONCE = 16
    private const val PROOF = 32
    private const val TAG_BITS = 128
}

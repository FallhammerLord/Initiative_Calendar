package net.fallhammer.initiative.pairs

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import initiative.core.PairWire
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Each pair's two keys, in Android's key store: they never leave it, never go
 * in the database or a backup. Known by the other phone's ID. Call off the
 * main thread.
 */
class PairKeys {
    private val store: KeyStore get() = KeyStore.getInstance(STORE).apply { load(null) }

    fun save(peerId: String, mac: ByteArray, seal: ByteArray) {
        val s = store
        s.setEntry(
            MAC + peerId,
            KeyStore.SecretKeyEntry(SecretKeySpec(mac, "HmacSHA256")),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build(),
        )
        s.setEntry(
            SEAL + peerId,
            KeyStore.SecretKeyEntry(SecretKeySpec(seal, KeyProperties.KEY_ALGORITHM_AES)),
            KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
    }

    /** This phone's keys for [peerId], tagged with its row; null when they're gone (after a restore, say). */
    fun load(peerId: String, peer: Long): PairWire.PairKeys? {
        val s = store
        val mac = s.getKey(MAC + peerId, null) as? SecretKey ?: return null
        val seal = s.getKey(SEAL + peerId, null) as? SecretKey ?: return null
        return PairWire.PairKeys(peer, mac, seal)
    }

    fun has(peerId: String): Boolean = store.containsAlias(MAC + peerId)

    fun delete(peerId: String) {
        val s = store
        runCatching { s.deleteEntry(MAC + peerId) }
        runCatching { s.deleteEntry(SEAL + peerId) }
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val MAC = "pair-mac-"
        const val SEAL = "pair-seal-"
    }
}

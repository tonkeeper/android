package com.tonapps.security.multichain

import com.tonapps.security.CipherAes
import com.tonapps.security.Security
import com.tonapps.security.spec.SimpleSecretSpec

/** The single encryption point of the package: algorithm, IV length and blob format live here. */
internal object CryptoSecure {

    const val SALT_LENGTH_BYTES: Int = 32
    const val IV_LENGTH_BYTES: Int = 12

    fun seal(key: ByteArray, data: ByteArray): SealedData {
        val iv = Security.randomBytes(IV_LENGTH_BYTES)
        val spec = SimpleSecretSpec(key.copyOf())
        val encrypted = CipherAes.encrypt(spec, iv, data)
        spec.destroy()
        return SealedData(iv, encrypted)
    }

    fun open(key: ByteArray, sealedData: SealedData): ByteArray {
        val spec = SimpleSecretSpec(key.copyOf())
        val decrypted = CipherAes.decrypt(spec, sealedData.iv, sealedData.ciphertext)
        spec.destroy()
        return decrypted
    }

    /** [seal] straight to the canonical single-BLOB-column form (see [SealedData]). */
    fun sealToBytes(key: ByteArray, data: ByteArray): ByteArray {
        val sealedData = seal(key, data)
        val bytes = sealedData.toBytes()
        sealedData.clear()
        return bytes
    }

    /** [open] from the canonical single-BLOB-column form (see [SealedData]). */
    fun openBytes(key: ByteArray, blob: ByteArray): ByteArray {
        val sealedData = SealedData.fromBytes(blob)
        return try {
            open(key, sealedData)
        } finally {
            sealedData.clear()
        }
    }

    fun deriveKey(pin: CharArray, salt: ByteArray): ByteArray? {
        return Security.argon2Hash(pin, salt)
    }

    fun calcVerification(derivedKey: ByteArray, size: Int): ByteArray {
        return Security.calcVerification(derivedKey, size)
    }
}

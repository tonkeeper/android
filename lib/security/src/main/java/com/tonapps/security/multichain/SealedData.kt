package com.tonapps.security.multichain

/**
 * An AES-GCM envelope: random IV + ciphertext. Canonical DB form is the concatenation
 * `iv ∥ ciphertext` — the IV is fixed-length and not secret. Blobs are parsed by position only,
 * so changing [CryptoSecure.IV_LENGTH_BYTES] requires a versioned header first.
 */
internal class SealedData(
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {

    fun toBytes(): ByteArray = iv + ciphertext

    fun clear() {
        iv.fill(0)
        ciphertext.fill(0)
    }

    companion object {

        fun fromBytes(bytes: ByteArray): SealedData {
            require(bytes.size > CryptoSecure.IV_LENGTH_BYTES) {
                "Sealed blob is too short: ${bytes.size} bytes"
            }

            return SealedData(
                iv = bytes.copyOfRange(0, CryptoSecure.IV_LENGTH_BYTES),
                ciphertext = bytes.copyOfRange(CryptoSecure.IV_LENGTH_BYTES, bytes.size),
            )
        }
    }
}

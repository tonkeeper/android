package com.tonkeeper.core.tonconnect

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hex
import com.google.crypto.tink.subtle.Random
import com.google.crypto.tink.subtle.X25519
import com.tonkeeper.core.tonconnect.models.TCKeyPair

internal class SessionCrypto(
    private val keyPair: TCKeyPair
) {

    private val nonceLength = 24

    val sessionId: String
        get() = keyPair.sessionId

    private fun createNonce(): ByteArray {
        return Random.randBytes(nonceLength)
    }

    fun encrypt(
        message: String,
        receiverPublicKey: ByteArray
    ): ByteArray {
        val encodedMessage = message.toByteArray(Charsets.UTF_8)
        val nonce = createNonce()
        val encrypted = X25519.computeSharedSecret(keyPair.privateKey.key.toByteArray(), receiverPublicKey)
        val aead = AesGcmJce(encrypted)
        return nonce + aead.encrypt(encodedMessage, nonce)
    }

    fun decrypt(cipherText: ByteArray, senderPublicKey: ByteArray): String {
        val nonce = cipherText.sliceArray(0 until nonceLength)
        val encryptedMessage = cipherText.sliceArray(nonceLength until cipherText.size)
        val sharedSecret = X25519.computeSharedSecret(keyPair.privateKey.key.toByteArray(), senderPublicKey)

        val aead = AesGcmJce(sharedSecret)
        val decryptedBytes = aead.decrypt(encryptedMessage, nonce)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
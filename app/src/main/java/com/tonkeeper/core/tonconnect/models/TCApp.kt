package com.tonkeeper.core.tonconnect.models

import android.net.Uri
import org.libsodium.jni.Sodium
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKey
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

data class TCApp(
    val url: String,
    val clientId: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
) {

    companion object {
        private fun nonce(): ByteArray {
            val nonce = ByteArray(Sodium.crypto_box_noncebytes())
            Sodium.randombytes_buf(nonce, nonce.size)
            return nonce
        }
    }

    val host: String
        get() {
            return Uri.parse(url).host ?: throw Exception("Invalid url: $url")
        }

    fun encrypt(body: String): ByteArray {
        return encrypt(body.toByteArray())
    }

    fun encrypt(body: ByteArray): ByteArray {
        val nonce = nonce()
        val cipher = ByteArray(body.size + Sodium.crypto_box_macbytes())
        Sodium.crypto_box_easy(cipher, body, body.size, nonce, hex(clientId), privateKey)
        return nonce + cipher
    }

    fun decrypt(body: String): ByteArray {
        return decrypt(body.toByteArray())
    }

    fun decrypt(body: ByteArray): ByteArray {
        val nonce = body.sliceArray(0 until Sodium.crypto_box_noncebytes())
        val cipher = body.sliceArray(Sodium.crypto_box_noncebytes() until body.size)
        val message = ByteArray(cipher.size - Sodium.crypto_box_macbytes())
        Sodium.crypto_box_open_easy(message, cipher, cipher.size, nonce, hex(clientId), privateKey)
        return message
    }

}
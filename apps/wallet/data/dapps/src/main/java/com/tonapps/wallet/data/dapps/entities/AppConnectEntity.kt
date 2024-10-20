package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.tonapps.base64.decodeBase64
import com.tonapps.security.CryptoBox
import com.tonapps.security.Sodium
import com.tonapps.security.hex
import org.json.JSONObject

data class AppConnectEntity(
    val accountId: String,
    val testnet: Boolean,
    val clientId: String,
    val type: Type,
    val appUrl: Uri,
    val keyPair: CryptoBox.KeyPair,
    val proofSignature: String?,
    val proofPayload: String?,
    val timestamp: Long = (System.currentTimeMillis() / 1000L),
    val pushEnabled: Boolean,
) {

    enum class Type(val value: Int) {
        Internal(1), External(2)
    }

    companion object {

        fun encryptMessage(
            remotePublicKey: ByteArray,
            localPrivateKey: ByteArray,
            body: ByteArray
        ): ByteArray {
            Log.d("AppConnectEntityLog", "encryptMessage: remotePublicKey = ${remotePublicKey.joinToString()}")
            Log.d("AppConnectEntityLog", "encryptMessage: localPrivateKey = ${localPrivateKey.joinToString()}")
            Log.d("AppConnectEntityLog", "encryptMessage: body = ${body.toString(Charsets.UTF_8)}")
            val nonce = CryptoBox.nonce()
            val cipher = ByteArray(body.size + Sodium.cryptoBoxMacBytes())
            Sodium.cryptoBoxEasy(cipher, body, body.size, nonce, remotePublicKey, localPrivateKey)
            return nonce + cipher
        }

        private fun decryptMessage(
            remotePublicKey: ByteArray,
            localPrivateKey: ByteArray,
            body: ByteArray
        ): ByteArray {
            val nonce = body.sliceArray(0 until Sodium.cryptoBoxNonceBytes())
            Log.d("AppConnectEntityLog", "nonce = ${nonce.joinToString()}")
            val cipher = body.sliceArray(Sodium.cryptoBoxNonceBytes() until body.size)
            Log.d("AppConnectEntityLog", "cipher = ${cipher.joinToString()}")
            val message = ByteArray(cipher.size - Sodium.cryptoBoxMacBytes())
            Log.d("AppConnectEntityLog", "message = ${message.joinToString()}")
            Sodium.cryptoBoxOpenEasy(message, cipher, cipher.size, nonce, remotePublicKey, localPrivateKey)
            return message
        }
    }

    val publicKeyHex: String by lazy {
        hex(keyPair.publicKey)
    }

    fun decryptMessage(body: ByteArray): ByteArray {
        return decryptMessage(clientId.hex(), keyPair.privateKey, body)
    }

    fun decryptEventMessage(message: String): JSONObject? {
        try {
            Log.d("AppConnectEntityLog", "decryptEventMessage: message = \"$message\"")
            val bytes = message.decodeBase64()
            Log.d("AppConnectEntityLog", "decryptEventMessage: bytes = ${bytes.joinToString()}")
            val decrypted = decryptMessage(bytes)
            Log.d("AppConnectEntityLog", "decryptEventMessage: decrypted = ${decrypted.joinToString()}")
            return JSONObject(decrypted.toString(Charsets.UTF_8))
        } catch (e: Throwable) {
            Log.e("AppConnectEntityLog", "decryptEventMessage: ", e)
            return null
        }
    }
}
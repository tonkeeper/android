package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.os.Parcelable
import android.util.Base64
import com.tonapps.extensions.asJSON
import com.tonapps.security.CryptoBox
import com.tonapps.security.Sodium
import com.tonapps.security.hex
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
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
): Parcelable {

    enum class Type(val value: Int) {
        Internal(1), External(2)
    }

    companion object {

        fun encryptMessage(
            remotePublicKey: ByteArray,
            localPrivateKey: ByteArray,
            body: ByteArray
        ): ByteArray {
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
            val cipher = body.sliceArray(Sodium.cryptoBoxNonceBytes() until body.size)
            val message = ByteArray(cipher.size - Sodium.cryptoBoxMacBytes())
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

    fun decryptEventMessage(message: String): JSONObject {
        val bytes = Base64.decode(message, Base64.NO_WRAP)
        val decrypted = decryptMessage(bytes)
        val string = decrypted.toString(Charsets.UTF_8)
        return string.asJSON()
    }
}
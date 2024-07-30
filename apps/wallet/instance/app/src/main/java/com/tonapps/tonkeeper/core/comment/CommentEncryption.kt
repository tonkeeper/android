package com.tonapps.tonkeeper.core.comment

import android.util.Log
import io.ktor.util.hex
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.Ed25519
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

object CommentEncryption {
    fun decryptComment(
        publicKey: PublicKeyEd25519,
        privateKey: PrivateKeyEd25519,
        cipherText: String,
        senderAddress: String
    ): String {
        val address = AddrStd(senderAddress).toString(bounceable = true, urlSafe = true)

        val decryptedData = decryptData(
            hex(cipherText),
            publicKey.key.toByteArray(),
            privateKey.key.toByteArray(),
            address.toByteArray()
        )

        return decryptedData.decodeToString()
    }

    private fun decryptData(
        data: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
        salt: ByteArray
    ): ByteArray {
        Log.d("decryptData", "data: ${hex(data)}")
        Log.d("decryptData", "publicKey: ${hex(publicKey)}")
        Log.d("decryptData", "privateKey: ${hex(privateKey)}")
        Log.d("decryptData", "salt: ${hex(salt)}")
        val theirPublicKey = ByteArray(publicKey.size)
        for (i in publicKey.indices) {
            theirPublicKey[i] = data[i] xor publicKey[i]
        }
        val sharedSecret = Ed25519.sharedKey(privateKey, theirPublicKey)
        return decryptDataImpl(data.sliceArray(publicKey.size until data.size), sharedSecret, salt)
    }

    private fun decryptDataImpl(
        encryptedData: ByteArray,
        sharedSecret: ByteArray,
        salt: ByteArray
    ): ByteArray {
        Log.d("decryptDataImpl", "encryptedData: ${hex(encryptedData)}")
        Log.d("decryptDataImpl", "sharedSecret: ${hex(sharedSecret)}")
        Log.d("decryptDataImpl", "salt: ${hex(salt)}")
        if (encryptedData.size < 16) throw IllegalArgumentException("Failed to decrypt: data is too small")
        if (encryptedData.size % 16 != 0) throw IllegalArgumentException("Failed to decrypt: data size is not divisible by 16")

        val msgKey = encryptedData.sliceArray(0 until 16)
        val data = encryptedData.sliceArray(16 until encryptedData.size)
        val cbcStateSecret = hmacSha512(sharedSecret, msgKey)

        return doDecrypt(cbcStateSecret, msgKey, data, salt)
    }

    private fun doDecrypt(
        cbcStateSecret: ByteArray,
        msgKey: ByteArray,
        encryptedData: ByteArray,
        salt: ByteArray
    ): ByteArray {
        Log.d("doDecrypt", "cbcStateSecret: ${hex(cbcStateSecret)}")
        Log.d("doDecrypt", "msgKey: ${hex(msgKey)}")
        Log.d("doDecrypt", "encryptedData: ${hex(encryptedData)}")
        Log.d("doDecrypt", "salt: ${hex(salt)}")
        val decryptedData = AesCbcState(cbcStateSecret).decrypt(encryptedData)
        val dataHash = hmacSha512(salt, decryptedData)

        val gotMsgKey = dataHash.sliceArray(0 until 16)

        if (!msgKey.contentEquals(gotMsgKey)) {
            Log.d("CommentEncryption", "msgKey: ${hex(msgKey)}, gotMsgKey: ${hex(gotMsgKey)}")
            throw IllegalArgumentException("Failed to decrypt: hash mismatch")
        }

        val prefixLength = decryptedData[0].toInt()
        if (prefixLength > decryptedData.size || prefixLength < 16) {
            throw IllegalArgumentException("Failed to decrypt: invalid prefix size")
        }

        return decryptedData.sliceArray(prefixLength until decryptedData.size)
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val keySpec = SecretKeySpec(key, "HmacSHA512")
        mac.init(keySpec)
        return mac.doFinal(data)
    }
}

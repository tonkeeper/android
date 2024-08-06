package com.tonapps.security

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AesCbcState(
    val key: ByteArray,
    val iv: ByteArray
) {

    companion object {

        private fun initCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(mode, keySpec, ivSpec)
            return cipher
        }
    }

    constructor(hash: ByteArray) : this(
        key = hash.sliceArray(0 until 32),
        iv = hash.sliceArray(32 until 48)
    )

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = initCipher(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(encryptedData)
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = initCipher(Cipher.ENCRYPT_MODE, key, iv)
        return cipher.doFinal(data)
    }
}
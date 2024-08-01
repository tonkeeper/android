package com.tonapps.tonkeeper.core.comment

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesCbcState(hash: ByteArray) {
    private val key: ByteArray
    private val iv: ByteArray

    init {
        if (hash.size < 48) throw IllegalArgumentException()

        key = hash.sliceArray(0 until 32)
        iv = hash.sliceArray(32 until 48)
    }

    private fun initCipher(mode: Int): Cipher {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(mode, keySpec, ivSpec)
        return cipher
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = initCipher(Cipher.DECRYPT_MODE)
        return cipher.doFinal(encryptedData)
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = initCipher(Cipher.ENCRYPT_MODE)
        return cipher.doFinal(data)
    }
}
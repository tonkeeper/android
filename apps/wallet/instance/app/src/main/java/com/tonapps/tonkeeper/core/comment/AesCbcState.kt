package com.tonapps.tonkeeper.core.comment

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesCbcState(hash: ByteArray) {
    private val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")

    init {
        if (hash.size < 48) throw IllegalArgumentException()

        val key = hash.sliceArray(0 until 32)
        val iv = hash.sliceArray(32 until 48)

        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        return cipher.doFinal(encryptedData)
    }
}
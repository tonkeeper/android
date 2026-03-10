package com.tonapps.security

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

object CipherAes {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BIT_SIZE = 128

    fun encrypt(key: Key, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val params = GCMParameterSpec(TAG_BIT_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, params, Security.secureRandom())
        return cipher.doFinal(data)
    }

    fun decrypt(key: Key, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val params = GCMParameterSpec(TAG_BIT_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, params, Security.secureRandom())
        return cipher.doFinal(data)
    }
}
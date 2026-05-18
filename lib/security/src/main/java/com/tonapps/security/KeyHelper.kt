package com.tonapps.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.tonapps.log.L
import java.security.KeyStore
import javax.crypto.KeyGenerator

object KeyHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_SIZE = 256

    private val keyStore: KeyStore by lazy {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE)
        store.load(null)
        store
    }

    fun createIfNotExists(alias: String) {
        L.d("KeyHelperLog", "createIfNotExists: $alias")
        if (!keyStore.containsAlias(alias)) {
            generateKey(alias)
        }
    }

    private fun generateKey(alias: String) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(defaultParameterBuilder(alias))
        try {
            generator.generateKey()
        } catch (e: Throwable) {
            // device locked
            L.e("KeyHelperLog", "generateKey: $e")
            throw e
        }
    }

    private fun defaultParameterBuilder(alias: String): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        builder.setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        builder.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        builder.setKeySize(KEY_SIZE)
        builder.setUserAuthenticationRequired(false)
        builder.setRandomizedEncryptionRequired(true)

        return builder.build()
    }
}

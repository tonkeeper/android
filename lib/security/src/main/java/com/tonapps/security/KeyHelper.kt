package com.tonapps.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.OutputStream
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
        if (!keyExists(alias)) {
            generateKey(alias)
        }
    }

    private fun generateKey(alias: String) {
        try {
            generateKeyWithStrongBoxBacked(alias)
        } catch (e: Throwable) {
            generateKey(getParameterKey(alias))
        }
    }

    private fun generateKeyWithStrongBoxBacked(alias: String) {
        val parameter = getParameterKeyStrongBox(alias)
        generateKey(parameter)
    }

    private fun generateKey(parameter: KeyGenParameterSpec) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(parameter)
    }

    private fun keyExists(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    private fun defaultParameterBuilder(alias: String): KeyGenParameterSpec.Builder {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        builder.setDigests(KeyProperties.DIGEST_SHA512)
        builder.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        builder.setKeySize(KEY_SIZE)
        builder.setUserAuthenticationRequired(false)
        builder.setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        return builder
    }

    private fun getParameterKey(alias: String): KeyGenParameterSpec {
        return defaultParameterBuilder(alias).build()
    }

    private fun getParameterKeyStrongBox(alias: String): KeyGenParameterSpec {
        val builder = defaultParameterBuilder(alias)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

}
package com.tonapps.security.vault

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.tonapps.extensions.getByteArray
import com.tonapps.extensions.putByteArray
import com.tonapps.security.Security
import com.tonapps.security.clear
import com.tonapps.security.decrypt
import com.tonapps.security.encrypt
import com.tonapps.security.safeDestroy
import com.tonapps.security.spec.SimpleSecretSpec
import javax.crypto.SecretKey

internal class MasterKey(
    private val prefs: SharedPreferences
) {

    private companion object {
        private const val KEY_SIZE = 32
        private const val IV_SIZE = 16

        private const val BODY_KEY = "master_body"
        private const val IV_KEY = "master_iv"
    }

    internal fun getSecret(passwordSecret: SecretKey): SecretKey? {
        val secretKey = runCatching {
            val iv = prefs.getByteArray(IV_KEY) ?: throw Exception("No iv")
            val encrypted = prefs.getByteArray(BODY_KEY)
            if (encrypted == null) {
                clear(iv)
                throw Exception("No body")
            }

            val key = passwordSecret.decrypt(iv, encrypted)
            clear(iv, encrypted)
            if (key == null) {
                throw Exception("Failed to decrypt")
            }

            SimpleSecretSpec(key)
        }.getOrNull()
        passwordSecret.safeDestroy()
        return secretKey
    }

    internal fun newSecret(passwordSecret: SecretKey): SecretKey? {
        val secretKey = Security.generatePrivateKey(KEY_SIZE)
        val secretEncoded = secretKey.encoded
        val iv = Security.randomBytes(IV_SIZE)

        val encrypted = passwordSecret.encrypt(iv, secretEncoded)
        passwordSecret.safeDestroy()
        secretEncoded.clear()

        if (encrypted == null) {
            secretKey.safeDestroy()
            clear(iv)
            return null
        }

        put(iv, encrypted)
        return secretKey
    }

    internal fun reEncryptSecret(oldPasswordSecret: SecretKey, newPasswordSecret: SecretKey): Boolean {
        val currentSecret = getSecret(oldPasswordSecret)
        if (currentSecret == null) {
            newPasswordSecret.safeDestroy()
            return false
        }

        val currentSecretEncoded = currentSecret.encoded
        val newIv = Security.randomBytes(IV_SIZE)

        val encrypted = newPasswordSecret.encrypt(newIv, currentSecretEncoded)

        newPasswordSecret.safeDestroy()
        currentSecretEncoded.clear()

        if (encrypted == null) {
            newIv.clear()
            return false
        }

        put(newIv, encrypted)
        return true
    }

    @SuppressLint("ApplySharedPref")
    private fun put(iv: ByteArray, encrypted: ByteArray) {
        prefs.edit()
            .putByteArray(IV_KEY, iv)
            .putByteArray(BODY_KEY, encrypted)
            .commit()

        clear(iv, encrypted)
    }
}
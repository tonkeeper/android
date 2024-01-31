package security.vault

import android.content.SharedPreferences
import security.Security
import security.atomicWrite
import security.clear
import security.decrypt
import security.encrypt
import security.getByteArray
import security.putByteArray
import security.spec.SimpleSecretSpec
import security.tryCallGC
import java.io.File
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

    fun getSecret(passwordSecret: SecretKey): SecretKey {
        val iv = getIv() ?: throw IllegalStateException("IV is not set")
        val encrypted = getBody()
        val key = passwordSecret.decrypt(iv, encrypted)
        passwordSecret.destroy()
        val secret = SimpleSecretSpec(key)
        iv.clear()
        encrypted.clear()
        return secret
    }

    fun newSecret(passwordSecret: SecretKey): SecretKey {
        val secretKey = Security.generatePrivateKey(KEY_SIZE)
        val iv = Security.randomBytes(IV_SIZE)

        val encrypted = passwordSecret.encrypt(iv, secretKey.encoded)
        passwordSecret.destroy()

        put(iv, encrypted)

        return secretKey
    }

    fun reEncryptSecret(oldPasswordSecret: SecretKey, newPasswordSecret: SecretKey) {
        val masterSecret = getSecret(oldPasswordSecret)
        val iv = Security.randomBytes(IV_SIZE)

        val encrypted = newPasswordSecret.encrypt(iv, masterSecret.encoded)
        newPasswordSecret.destroy()

        put(iv, encrypted)

        tryCallGC()
    }

    private fun put(iv: ByteArray, encrypted: ByteArray) {
        putIv(iv)
        saveBody(encrypted)
    }

    private fun getIv(): ByteArray? {
        return prefs.getByteArray(IV_KEY)
    }

    private fun putIv(iv: ByteArray) {
        prefs.edit().putByteArray(IV_KEY, iv).apply()
    }

    private fun getBody(): ByteArray {
        return prefs.getByteArray(BODY_KEY) ?: throw IllegalStateException("Body is not set")
    }

    private fun saveBody(encrypted: ByteArray) {
        prefs.edit().putByteArray(BODY_KEY, encrypted).apply()
    }
}
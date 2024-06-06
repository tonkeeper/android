package com.tonapps.security.vault

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.tonapps.security.clear
import com.tonapps.security.safeDestroy
import com.tonapps.security.tryCallGC
import javax.crypto.SecretKey

open class Vault(
    private val prefs: SharedPreferences
) {

    private val passwordKey = PasswordKey(prefs)
    private val masterKey = MasterKey(prefs)
    private val storage = Storage(prefs)

    @SuppressLint("ApplySharedPref")
    fun clear() {
        prefs.edit().clear().commit()
    }

    fun hasPassword(): Boolean {
        return !passwordKey.isEmpty()
    }

    fun isValidPassword(password: CharArray): Boolean {
        val valid = passwordKey.isValid(password)
        tryCallGC()
        return valid
    }

    fun get(secret: SecretKey, id: Long): ByteArray {
        return storage.get(id, secret)
    }

    fun put(secret: SecretKey, id: Long, data: ByteArray) {
        storage.put(secret, id, data)
    }

    fun delete(secret: SecretKey, id: Long) {
        storage.put(secret, id, ByteArray(0))
    }

    fun getMasterSecret(password: CharArray): SecretKey {
        val passwordSecret = passwordKey.create(password) ?: throw IllegalStateException("Password secret is null")
        val masterSecret = masterKey.getSecret(passwordSecret)
        passwordSecret.safeDestroy()
        tryCallGC()

        return masterSecret ?: throw IllegalStateException("Master secret is null")
    }

    fun createMasterSecret(password: CharArray): SecretKey {
        val passwordSecret = passwordKey.set(password) ?: throw IllegalStateException("Password secret is null")
        val masterSecret = masterKey.newSecret(passwordSecret)
        passwordSecret.safeDestroy()
        tryCallGC()

        return masterSecret ?: throw IllegalStateException("Master secret is null")
    }

    fun changePassword(newPassword: CharArray, oldPassword: CharArray): Boolean {
        val oldPasswordSecret = passwordKey.create(oldPassword)
        if (oldPasswordSecret == null) {
            newPassword.clear()
            return false
        }

        val newPasswordSalt = PasswordKey.generateSalt()
        val newPasswordSecret = PasswordKey.generateSecretKey(newPassword, newPasswordSalt)

        if (newPasswordSecret == null) {
            oldPasswordSecret.safeDestroy()
            newPasswordSalt.clear()
            return false
        }

        val newPasswordVerification = PasswordKey.calcVerification(newPasswordSecret.encoded)
        val reEncrypted = masterKey.reEncryptSecret(oldPasswordSecret, newPasswordSecret)
        if (reEncrypted) {
            passwordKey.setSaltAndVerification(newPasswordSalt, newPasswordVerification)
        }

        oldPasswordSecret.safeDestroy()
        newPasswordSecret.safeDestroy()
        clear(newPasswordSalt, newPasswordVerification)

        tryCallGC()
        return reEncrypted
    }
}
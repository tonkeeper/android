package com.tonapps.security.vault

import android.content.SharedPreferences
import com.tonapps.security.clear
import com.tonapps.security.safeDestroy
import com.tonapps.security.tryCallGC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

open class Vault(
    private val prefs: SharedPreferences
) {

    private val coroutineContext = Dispatchers.IO + SupervisorJob()
    private val passwordKey = PasswordKey(prefs)
    private val masterKey = MasterKey(prefs)
    private val storage = Storage(prefs)

    suspend fun deleteAll() = withContext(coroutineContext) {
        prefs.edit().clear().apply()
    }

    suspend fun hasPassword(): Boolean = withContext(coroutineContext) {
        !passwordKey.isEmpty()
    }

    suspend fun isValidPassword(password: CharArray): Boolean = withContext(coroutineContext) {
        val valid = passwordKey.isValid(password)
        tryCallGC()
        valid
    }

    suspend fun get(secret: SecretKey, id: Long): ByteArray = withContext(coroutineContext) {
        storage.get(id, secret)
    }

    suspend fun put(secret: SecretKey, id: Long, data: ByteArray) = withContext(coroutineContext) {
        storage.put(secret, id, data)
    }

    suspend fun delete(secret: SecretKey, id: Long) = withContext(coroutineContext) {
        storage.put(secret, id, ByteArray(0))
    }

    suspend fun getMasterSecret(password: CharArray): SecretKey = withContext(coroutineContext) {
        val passwordSecret = passwordKey.create(password) ?: throw IllegalStateException("Password secret is null")
        val masterSecret = masterKey.getSecret(passwordSecret)
        passwordSecret.safeDestroy()
        tryCallGC()

        masterSecret ?: throw IllegalStateException("Master secret is null")
    }

    suspend fun createMasterSecret(password: CharArray): SecretKey = withContext(coroutineContext) {
        val passwordSecret = passwordKey.set(password) ?: throw IllegalStateException("Password secret is null")
        val masterSecret = masterKey.newSecret(passwordSecret)
        passwordSecret.safeDestroy()
        tryCallGC()

        masterSecret ?: throw IllegalStateException("Master secret is null")
    }

    suspend fun changePassword(
        newPassword: CharArray,
        oldPassword: CharArray
    ): Boolean = withContext(coroutineContext) {
        val oldPasswordSecret = passwordKey.create(oldPassword)
        if (oldPasswordSecret == null) {
            newPassword.clear()
            return@withContext false
        }

        val newPasswordSalt = PasswordKey.generateSalt()
        val newPasswordSecret = PasswordKey.generateSecretKey(newPassword, newPasswordSalt)

        if (newPasswordSecret == null) {
            oldPasswordSecret.safeDestroy()
            newPasswordSalt.clear()
            return@withContext false
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
        return@withContext reEncrypted
    }
}
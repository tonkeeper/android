package security.vault

import android.content.SharedPreferences
import android.util.Log
import security.clear
import security.hex
import security.tryCallGC
import javax.crypto.SecretKey

open class Vault(
    private val prefs: SharedPreferences
) {

    private val passwordKey = PasswordKey(prefs)
    private val masterKey = MasterKey(prefs)
    private val storage = Storage(prefs)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasPassword(): Boolean {
        return !passwordKey.isEmpty()
    }

    fun isValidPassword(password: CharArray) = passwordKey.isValid(password)

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
        val passwordSecret = passwordKey.create(password)
        return masterKey.getSecret(passwordSecret)
    }

    fun createMasterSecret(password: CharArray): SecretKey {
        val passwordSecret = passwordKey.set(password)
        return masterKey.newSecret(passwordSecret)
    }

    fun changePassword(newPassword: CharArray, oldPassword: CharArray) {
        val oldPasswordSecret = passwordKey.create(oldPassword)

        val newPasswordSalt = PasswordKey.generateSalt()
        val newPasswordSecret = PasswordKey.generateSecretKey(newPassword, newPasswordSalt)
        val newPasswordVerification = PasswordKey.calcVerification(newPasswordSecret.encoded)

        masterKey.reEncryptSecret(oldPasswordSecret, newPasswordSecret)
        passwordKey.setSaltAndVerification(newPasswordSalt, newPasswordVerification)
        newPasswordSalt.clear()

        tryCallGC()
    }
}
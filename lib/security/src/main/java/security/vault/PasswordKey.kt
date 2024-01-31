package security.vault

import android.content.SharedPreferences
import android.util.Log
import security.spec.SimpleSecretSpec
import security.Security
import security.clear
import security.clearWithGC
import security.getByteArray
import security.hex
import security.putByteArray
import security.tryCallGC
import java.security.MessageDigest
import javax.crypto.SecretKey

internal class PasswordKey(
    private val prefs: SharedPreferences,
) {

    companion object {
        private const val SALT_KEY = "password_salt"
        private const val SALT_SIZE = 32

        private const val VERIFICATION_KEY = "password_verification"
        private const val VERIFICATION_SIZE = 4

        fun generateSalt() = Security.randomBytes(SALT_SIZE)

        fun generateSecretKey(password: CharArray, salt: ByteArray): SecretKey {
            val hash = Security.argon2Hash(password, salt) ?: throw IllegalStateException("failed password hashing")
           val secret = SimpleSecretSpec(hash)
            password.clear()
            return secret
        }

        fun calcVerification(input: ByteArray): ByteArray {
            val verification = Security.calcVerification(input, VERIFICATION_SIZE)
            input.clear()
            return verification
        }
    }

    fun isEmpty(): Boolean {
        return !prefs.contains(SALT_KEY) || !prefs.contains(VERIFICATION_KEY)
    }

    /**
     * Quickly check valid password without need to decrypt master key.
     * Only for UI and other non-sensitive operations.
     */
    fun isValid(password: CharArray): Boolean {
        var currentVerification: ByteArray? = null
        var secret: SecretKey? = null
        var verification: ByteArray? = null
        val valid = try {
            currentVerification = getVerification() ?: throw IllegalStateException("verification is not saved")
            secret = create(password)
            verification = calcVerification(secret.encoded)
            MessageDigest.isEqual(currentVerification, verification)
        } catch (e: Throwable) {
            false
        }
        secret?.destroy()
        clearWithGC(currentVerification, verification)
        tryCallGC()
        return valid
    }

    fun create(password: CharArray): SecretKey {
        val salt = getSalt() ?: throw IllegalStateException("salt is not saved")
        val secret = generateSecretKey(password, salt)
        salt.clear()
        return secret
    }

    fun set(password: CharArray): SecretKey {
        val salt = generateSalt()
        val secret = generateSecretKey(password, salt)
        val verification = calcVerification(secret.encoded)
        setSaltAndVerification(salt, verification)
        salt.clear()
        return secret
    }

    private fun getSalt(): ByteArray? {
        return prefs.getByteArray(SALT_KEY)
    }

    fun setSaltAndVerification(salt: ByteArray, verification: ByteArray) {
        prefs.edit()
            .putByteArray(SALT_KEY, salt)
            .putByteArray(VERIFICATION_KEY, verification)
            .apply()
    }

    private fun getVerification(): ByteArray? {
        return prefs.getByteArray(VERIFICATION_KEY)
    }
}
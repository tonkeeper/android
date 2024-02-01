package security.vault

import android.annotation.SuppressLint
import android.content.SharedPreferences
import security.spec.SimpleSecretSpec
import security.Security
import security.clear
import security.getByteArray
import security.putByteArray
import security.safeDestroy
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

        fun generateSecretKey(password: CharArray, salt: ByteArray): SecretKey? {
            val hash = Security.argon2Hash(password, salt)
            password.clear()
            if (hash == null) {
                return null
            }
            return SimpleSecretSpec(hash)
        }

        fun calcVerification(input: ByteArray): ByteArray {
            val verification = Security.calcVerification(input, VERIFICATION_SIZE)
            input.clear()
            return verification
        }
    }

    internal fun isEmpty(): Boolean {
        return !prefs.contains(SALT_KEY) || !prefs.contains(VERIFICATION_KEY)
    }

    /**
     * Quickly check valid password without need to decrypt master key.
     * Only for UI and other non-sensitive operations.
     */
    internal fun isValid(password: CharArray): Boolean {
        var currentVerification: ByteArray? = null
        var secret: SecretKey? = null
        var verification: ByteArray? = null

        val valid = try {
            currentVerification = prefs.getByteArray(VERIFICATION_KEY) ?: throw IllegalStateException("verification is null")
            secret = create(password) ?: throw IllegalStateException("failed to create secret key")
            verification = calcVerification(secret.encoded)
            MessageDigest.isEqual(currentVerification, verification)
        } catch (e: Throwable) {
            false
        }

        secret?.safeDestroy()
        clear(currentVerification, verification)

        return valid
    }

    internal fun create(password: CharArray): SecretKey? {
        val salt = prefs.getByteArray(SALT_KEY)
        if (salt == null) {
            password.clear()
            return null
        }

        val secret = generateSecretKey(password, salt)
        salt.clear()
        return secret
    }

    internal fun set(password: CharArray): SecretKey? {
        val salt = generateSalt()
        val secret = generateSecretKey(password, salt)

        if (secret == null) {
            salt.clear()
            return null
        }

        val verification = calcVerification(secret.encoded)
        setSaltAndVerification(salt, verification)
        return secret
    }

    @SuppressLint("ApplySharedPref")
    internal fun setSaltAndVerification(salt: ByteArray, verification: ByteArray) {
        prefs.edit()
            .putByteArray(SALT_KEY, salt)
            .putByteArray(VERIFICATION_KEY, verification)
            .commit()

        clear(salt, verification)
    }
}
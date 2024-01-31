package security

import android.os.Build
import android.security.KeyChain
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object Security {

    fun generatePrivateKey(keySize: Int): SecretKey {
        return try {
            val generator = KeyGenerator.getInstance("AES")
            val random = secureRandom()
            generator.init(keySize * 8, random)
            generator.generateKey()
        } catch (e: Throwable) {
            SecretKeySpec(randomBytes(keySize), "AES")
        }
    }

    fun calcVerification(input: ByteArray, size: Int): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(input)
        val digest = messageDigest.digest()
        val verification = ByteArray(size)
        digest.copyInto(verification, 0, 0, size)
        digest.clear()
        return verification
    }

    fun argon2Hash(password: CharArray, salt: ByteArray): ByteArray? {
        return Sodium.argon2IdHash(password, salt, 32)
    }

    fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom().nextBytes(bytes)
        return bytes
    }

    fun secureRandom(): SecureRandom {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }
    }

}
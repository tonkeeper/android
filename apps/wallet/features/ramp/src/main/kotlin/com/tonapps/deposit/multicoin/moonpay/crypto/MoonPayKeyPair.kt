package com.tonapps.deposit.multicoin.moonpay.crypto

import com.tonapps.deposit.multicoin.moonpay.frame.moonPayFrameJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.util.encoders.DecoderException
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import java.util.Base64

class MoonPayKeyPair internal constructor(private val privateKey: X25519PrivateKeyParameters) {

    val publicKeyHex: String = Hex.toHexString(privateKey.generatePublicKey().encoded)

    constructor() : this(X25519PrivateKeyParameters(SecureRandom()))

    fun decrypt(encryptedCredentials: String): MoonPayCredentials {
        val plaintext = decryptPlaintext(envelope(encryptedCredentials))
        return try {
            moonPayFrameJson.decodeFromString(MoonPayCredentials.serializer(), plaintext.decodeToString())
        } catch (e: SerializationException) {
            throw MoonPayCryptoException.InvalidPlaintext(e)
        } catch (e: IllegalArgumentException) {
            throw MoonPayCryptoException.InvalidPlaintext(e)
        }
    }

    private fun decryptPlaintext(envelope: Envelope): ByteArray {
        val iv = hex(envelope.iv, "iv")
        val ephemeralPublicKey = hex(envelope.ephemeralPublicKey, "ephemeralPublicKey")
        val ciphertextWithTag = hex(envelope.ciphertext, "ciphertext")
        if (ciphertextWithTag.size <= TAG_LENGTH) {
            throw MoonPayCryptoException.InvalidTag()
        }
        val key = symmetricKey(sharedSecret(ephemeralPublicKey))
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        return try {
            cipher.init(false, AEADParameters(KeyParameter(key), TAG_LENGTH * Byte.SIZE_BITS, iv))
            val plaintext = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
            val length = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, plaintext, 0)
            cipher.doFinal(plaintext, length)
            plaintext
        } catch (e: InvalidCipherTextException) {
            throw MoonPayCryptoException.DecryptionFailed(e)
        } catch (e: IllegalArgumentException) {
            throw MoonPayCryptoException.DecryptionFailed(e)
        }
    }

    private fun sharedSecret(ephemeralPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement().apply { init(privateKey) }
        val secret = ByteArray(agreement.agreementSize)
        try {
            agreement.calculateAgreement(X25519PublicKeyParameters(ephemeralPublicKey), secret, 0)
        } catch (e: IllegalArgumentException) {
            throw MoonPayCryptoException.InvalidEphemeralPublicKey(e)
        } catch (e: IllegalStateException) {
            throw MoonPayCryptoException.InvalidEphemeralPublicKey(e)
        }
        return secret
    }

    @Serializable
    private class Envelope(
        val iv: String,
        val ephemeralPublicKey: String,
        val ciphertext: String,
    )

    private companion object {

        const val TAG_LENGTH = 16
        const val KEY_LENGTH = 32

        fun envelope(encryptedCredentials: String): Envelope {
            return try {
                val json = Base64.getDecoder().decode(encryptedCredentials).decodeToString()
                moonPayFrameJson.decodeFromString(Envelope.serializer(), json)
            } catch (e: IllegalArgumentException) {
                throw MoonPayCryptoException.InvalidEnvelope(e)
            }
        }

        fun hex(value: String, field: String): ByteArray {
            val digits = if (value.startsWith("0x", ignoreCase = true)) {
                value.substring(2).ifEmpty { throw MoonPayCryptoException.InvalidHex(field) }
            } else {
                value
            }
            return try {
                Hex.decodeStrict(digits)
            } catch (e: DecoderException) {
                throw MoonPayCryptoException.InvalidHex(field, e)
            }
        }

        fun symmetricKey(sharedSecret: ByteArray): ByteArray {
            val generator = HKDFBytesGenerator(SHA256Digest())
            generator.init(HKDFParameters(sharedSecret, ByteArray(0), ByteArray(0)))
            return ByteArray(KEY_LENGTH).also { generator.generateBytes(it, 0, it.size) }
        }
    }
}

@Serializable
data class MoonPayCredentials(
    val accessToken: String,
    val clientToken: String,
    val expiresAt: String,
) {
    override fun toString(): String = "MoonPayCredentials(expiresAt=$expiresAt)"
}

sealed class MoonPayCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class InvalidEnvelope(cause: Throwable) :
        MoonPayCryptoException("MoonPay credentials envelope is not base64 JSON", cause)

    class InvalidHex(val field: String, cause: Throwable? = null) :
        MoonPayCryptoException("MoonPay credentials field $field is not hex", cause)

    class InvalidEphemeralPublicKey(cause: Throwable) :
        MoonPayCryptoException("MoonPay credentials carry an invalid ephemeral public key", cause)

    class InvalidTag : MoonPayCryptoException("MoonPay credentials ciphertext is shorter than the authentication tag")

    class DecryptionFailed(cause: Throwable) : MoonPayCryptoException("MoonPay credentials could not be decrypted", cause)

    class InvalidPlaintext(cause: Throwable) :
        MoonPayCryptoException("MoonPay credentials plaintext is not the expected JSON", cause)
}

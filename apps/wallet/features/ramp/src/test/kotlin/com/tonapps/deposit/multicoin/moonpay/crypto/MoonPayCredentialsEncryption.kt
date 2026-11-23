package com.tonapps.deposit.multicoin.moonpay.crypto

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
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import java.util.Base64

private const val X25519_KEY_LENGTH = 32
private const val AES_KEY_LENGTH = 32
private const val IV_LENGTH = 12
private const val TAG_BITS = 128

internal fun encryptMoonPayCredentials(
    plaintext: String,
    to: String,
    tamperLastByte: Boolean = false,
    prefix: String = "",
): String {
    val random = SecureRandom()
    val ephemeral = X25519PrivateKeyParameters(random)
    val secret = ByteArray(X25519_KEY_LENGTH)
    X25519Agreement().apply { init(ephemeral) }
        .calculateAgreement(X25519PublicKeyParameters(Hex.decodeStrict(to)), secret, 0)
    val key = ByteArray(AES_KEY_LENGTH)
    HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(secret, ByteArray(0), ByteArray(0))) }
        .generateBytes(key, 0, key.size)
    val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
    cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, iv))
    val input = plaintext.toByteArray()
    val ciphertextWithTag = ByteArray(cipher.getOutputSize(input.size))
    val length = cipher.processBytes(input, 0, input.size, ciphertextWithTag, 0)
    cipher.doFinal(ciphertextWithTag, length)
    if (tamperLastByte) {
        ciphertextWithTag[ciphertextWithTag.lastIndex] = (ciphertextWithTag.last().toInt() xor 0xFF).toByte()
    }
    return envelope(
        iv = prefix + Hex.toHexString(iv),
        ephemeralPublicKey = prefix + Hex.toHexString(ephemeral.generatePublicKey().encoded),
        ciphertext = prefix + Hex.toHexString(ciphertextWithTag),
    )
}

internal fun envelope(iv: String, ephemeralPublicKey: String, ciphertext: String): String {
    return base64("""{"iv":"$iv","ephemeralPublicKey":"$ephemeralPublicKey","ciphertext":"$ciphertext"}""")
}

internal fun base64(json: String): String = Base64.getEncoder().encodeToString(json.toByteArray())

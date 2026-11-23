package com.tonapps.deposit.multicoin.moonpay.crypto

import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPayKeyPairTest {

    private val plaintext = """{"accessToken":"access-1","clientToken":"client-1","expiresAt":"2026-03-27T12:30:00.000Z"}"""

    @Test
    fun `decrypts what the frame encrypted to it`() {
        val keyPair = MoonPayKeyPair()

        val credentials = keyPair.decrypt(encryptMoonPayCredentials(plaintext, to = keyPair.publicKeyHex))

        assertEquals(MoonPayCredentials("access-1", "client-1", "2026-03-27T12:30:00.000Z"), credentials)
    }

    @Test
    fun `the public key is a raw 32-byte key in hex`() {
        val keyPair = MoonPayKeyPair()

        assertEquals(64, keyPair.publicKeyHex.length)
        assertEquals(32, Hex.decodeStrict(keyPair.publicKeyHex).size)
        assertNotEquals(MoonPayKeyPair().publicKeyHex, keyPair.publicKeyHex)
    }

    @Test
    fun `hex fields may carry a 0x prefix`() {
        val keyPair = MoonPayKeyPair()

        val credentials = keyPair.decrypt(encryptMoonPayCredentials(plaintext, to = keyPair.publicKeyHex, prefix = "0X"))

        assertEquals("access-1", credentials.accessToken)
    }

    @Test
    fun `another recipient cannot decrypt`() {
        val encrypted = encryptMoonPayCredentials(plaintext, to = MoonPayKeyPair().publicKeyHex)

        assertFailsWith<MoonPayCryptoException.DecryptionFailed> { MoonPayKeyPair().decrypt(encrypted) }
    }

    @Test
    fun `a tampered ciphertext fails`() {
        val keyPair = MoonPayKeyPair()

        val encrypted = encryptMoonPayCredentials(plaintext, to = keyPair.publicKeyHex, tamperLastByte = true)

        assertFailsWith<MoonPayCryptoException.DecryptionFailed> { keyPair.decrypt(encrypted) }
    }

    @Test
    fun `malformed envelopes`() {
        val keyPair = MoonPayKeyPair()

        assertFailsWith<MoonPayCryptoException.InvalidEnvelope> { keyPair.decrypt("not base64!") }
        assertFailsWith<MoonPayCryptoException.InvalidEnvelope> { keyPair.decrypt(base64("""{"iv":"00"}""")) }
        assertEquals(
            "iv",
            assertFailsWith<MoonPayCryptoException.InvalidHex> { keyPair.decrypt(envelope("zz", "00", "00")) }.field,
        )
        assertEquals(
            "ephemeralPublicKey",
            assertFailsWith<MoonPayCryptoException.InvalidHex> { keyPair.decrypt(envelope("00", "0", "00")) }.field,
        )
        assertEquals(
            "ciphertext",
            assertFailsWith<MoonPayCryptoException.InvalidHex> { keyPair.decrypt(envelope("00", "00", "0g")) }.field,
        )
        assertEquals(
            "iv",
            assertFailsWith<MoonPayCryptoException.InvalidHex> { keyPair.decrypt(envelope("0x", "00", "00")) }.field,
        )
        assertFailsWith<MoonPayCryptoException.InvalidTag> {
            keyPair.decrypt(envelope("00", "00", "00".repeat(16)))
        }
        assertFailsWith<MoonPayCryptoException.InvalidEphemeralPublicKey> {
            keyPair.decrypt(envelope("00", "00", "00".repeat(17)))
        }
    }

    @Test
    fun `the plaintext must be credentials`() {
        val keyPair = MoonPayKeyPair()

        val encrypted = encryptMoonPayCredentials("""{"accessToken":"only"}""", to = keyPair.publicKeyHex)

        assertFailsWith<MoonPayCryptoException.InvalidPlaintext> { keyPair.decrypt(encrypted) }
    }

    @Test
    fun `credentials never print their tokens`() {
        val credentials = MoonPayCredentials("secret-access", "secret-client", "2026-03-27T12:30:00Z")

        assertFalse("$credentials".contains("secret"))
        assertTrue("$credentials".contains("2026-03-27T12:30:00Z"))
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("expected ${T::class.simpleName}, got $failure", failure is T)
        return failure as T
    }
}

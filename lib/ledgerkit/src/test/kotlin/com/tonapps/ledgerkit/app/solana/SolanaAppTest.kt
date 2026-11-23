package com.tonapps.ledgerkit.app.solana

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.apdu.Bip32Path
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration

class SolanaAppTest {

    private val path = Bip32Path.parse("m/44'/501'/0'/0'")

    @Test
    fun `public key is read silently with a timeout and on screen without one`() = runTest {
        val sent = mutableListOf<Pair<ByteArray, Duration?>>()
        val key = ByteArray(32) { it.toByte() }
        val app = SolanaApp { apdu, timeout ->
            sent += apdu to timeout
            ApduResponse(key, 0x9000)
        }
        assertArrayEquals(key, app.getPublicKey(path, display = false))
        assertArrayEquals(key, app.getPublicKey(path, display = true))
        assertEquals(listOf(0x05, 0x05), sent.map { it.first[1].toInt() and 0xff })
        assertEquals(listOf(0x00, 0x01), sent.map { it.first[2].toInt() and 0xff })
        assertArrayEquals(path.encode(), sent[0].first.copyOfRange(5, sent[0].first.size))
        assertEquals(ApduChannel.NON_INTERACTIVE_TIMEOUT, sent[0].second)
        assertNull(sent[1].second)
    }

    @Test
    fun `short message goes in one apdu after the signer count and the path`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val signature = ByteArray(64) { 9 }
        val app = SolanaApp { apdu, _ ->
            sent += apdu
            ApduResponse(signature, 0x9000)
        }
        val message = ByteArray(120) { 3 }
        assertArrayEquals(signature, app.signMessage(path, message))
        assertEquals(1, sent.size)
        assertEquals(0x06, sent[0][1].toInt() and 0xff)
        assertEquals(0x01, sent[0][2].toInt() and 0xff)
        assertEquals(0x00, sent[0][3].toInt() and 0xff)
        assertArrayEquals(byteArrayOf(1) + path.encode() + message, sent[0].copyOfRange(5, sent[0].size))
    }

    @Test
    fun `long message is split with more and extend flags`() = runTest {
        val sent = mutableListOf<Pair<ByteArray, Duration?>>()
        val app = SolanaApp { apdu, timeout ->
            sent += apdu to timeout
            ApduResponse(if (sent.size == 3) { ByteArray(64) } else { ByteArray(0) }, 0x9000)
        }
        val message = ByteArray(600) { it.toByte() }
        app.signMessage(path, message)
        assertEquals(listOf(0x02, 0x03, 0x01), sent.map { it.first[3].toInt() and 0xff })
        assertEquals(listOf(255, 255, 600 + 1 + path.encode().size - 510), sent.map { it.first[4].toInt() and 0xff })
        val payload = sent.map { it.first.copyOfRange(5, it.first.size) }.reduce(ByteArray::plus)
        assertArrayEquals(byteArrayOf(1) + path.encode() + message, payload)
        assertEquals(listOf(ApduChannel.NON_INTERACTIVE_TIMEOUT, ApduChannel.NON_INTERACTIVE_TIMEOUT, null), sent.map { it.second })
    }

    @Test
    fun `unrecognised transaction without blind signing asks for the setting`() = runTest {
        val app = SolanaApp { _, _ -> ApduResponse(ByteArray(0), 0x6808) }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Solana, LedgerAppSetting.BlindSigning), failureOf { app.signMessage(path, ByteArray(10)) })
    }

    @Test
    fun `refusal and a foreign signer are reported as such`() = runTest {
        assertEquals(
            LedgerFailure.UserRefused(0x6985),
            failureOf { SolanaApp { _, _ -> ApduResponse(ByteArray(0), 0x6985) }.signMessage(path, ByteArray(10)) },
        )
        assertEquals(
            LedgerFailure.InvalidPayload(0x6a81, "The transaction is not signed by this account"),
            failureOf { SolanaApp { _, _ -> ApduResponse(ByteArray(0), 0x6a81) }.signMessage(path, ByteArray(10)) },
        )
    }

    @Test
    fun `configuration reports the version and the blind signing switch`() = runTest {
        val app = SolanaApp { _, _ -> ApduResponse(byteArrayOf(1, 0, 1, 16, 0, 0, 0), 0x9000) }
        assertEquals(SolanaAppConfiguration(version = "1.16.0", blindSigningEnabled = true), app.getConfiguration())
    }

    private suspend fun failureOf(block: suspend () -> Unit): LedgerFailure? = try {
        block()
        fail()
        null
    } catch (e: LedgerFailureException) {
        e.failure
    }
}

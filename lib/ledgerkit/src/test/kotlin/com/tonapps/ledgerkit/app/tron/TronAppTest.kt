package com.tonapps.ledgerkit.app.tron

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.util.Base58
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TronAppTest {

    private val publicKey = hex(
        "04" +
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
            "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
    )

    @Test
    fun `address derives from the public key`() {
        assertEquals("TMVQGm1qAQYVdetCeGRRkTWYYrLXuHK2HC", TronApp.addressFromPublicKey(publicKey))
        assertArrayEquals(hex("417e5f4552091a69125d5dfcb7b8c2659029395bdf"), Base58.decodeWithChecksum("TMVQGm1qAQYVdetCeGRRkTWYYrLXuHK2HC"))
    }

    @Test
    fun `address response is verified against the public key`() {
        val good = byteArrayOf(65) + publicKey + byteArrayOf(34) + "TMVQGm1qAQYVdetCeGRRkTWYYrLXuHK2HC".toByteArray()
        assertEquals("TMVQGm1qAQYVdetCeGRRkTWYYrLXuHK2HC", TronApp.parseAddress(good).address)
        val tampered = byteArrayOf(65) + publicKey + byteArrayOf(34) + "TLsV52sRDL79HXGGm9yzwKibb6BeruhUzy".toByteArray()
        try {
            TronApp.parseAddress(tampered)
            fail()
        } catch (e: LedgerFailureException) {
            assertTrue(e.failure is LedgerFailure.InvalidPayload)
        }
    }

    @Test
    fun `small transaction is sent as a single chunk with the path`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val app = TronApp(channel(sent, ByteArray(65) { 7 }))
        val raw = field(1, ByteArray(2)) + field(4, ByteArray(8)) + field(11, ByteArray(100))
        val signature = app.signTransaction(Bip32Path.parse("m/44'/195'/0'/0/0"), raw)
        assertEquals(1, sent.size)
        assertEquals(0x10, sent[0][2].toInt() and 0xff)
        assertArrayEquals(Bip32Path.parse("m/44'/195'/0'/0/0").encode() + raw, sent[0].copyOfRange(5, sent[0].size))
        assertEquals(65, signature.size)
    }

    @Test
    fun `large transaction is split on protobuf field boundaries`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val app = TronApp(channel(sent, ByteArray(65)))
        val fields = listOf(field(1, ByteArray(2)), field(11, ByteArray(200)), field(14, ByteArray(230)), field(18, ByteArray(40)))
        app.signTransaction(Bip32Path.parse("m/44'/195'/0'/0/0"), fields.reduce(ByteArray::plus))
        assertEquals(listOf(0x00, 0x80, 0x90), sent.map { it[2].toInt() and 0xff })
        assertTrue(sent.all { (it[4].toInt() and 0xff) <= 250 })
        assertArrayEquals(fields[2], sent[1].copyOfRange(5, sent[1].size))
        assertArrayEquals(fields[3], sent[2].copyOfRange(5, sent[2].size))
    }

    @Test
    fun `oversized contract field requires hash signing`() = runTest {
        val app = TronApp(channel(mutableListOf(), ByteArray(65)))
        try {
            app.signTransaction(Bip32Path.parse("m/44'/195'/0'/0/0"), field(11, ByteArray(300)))
            fail()
        } catch (e: LedgerFailureException) {
            assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash), e.failure)
        }
    }

    @Test
    fun `personal message carries the path and big endian length in the first chunk`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val app = TronApp(channel(sent, ByteArray(65)))
        val message = ByteArray(400) { 1 }
        app.signPersonalMessage(Bip32Path.parse("m/44'/195'/0'/0/0"), message)
        assertEquals(listOf(0x00, 0x80), sent.map { it[2].toInt() and 0xff })
        assertArrayEquals(byteArrayOf(0, 0, 1, 0x90.toByte()), sent[0].copyOfRange(5 + 21, 5 + 25))
        assertEquals(400, (sent[0].size - 5 - 25) + (sent[1].size - 5))
    }

    @Test
    fun `configuration bit layout depends on app version`() = runTest {
        val modern = TronApp { _, _ -> ApduResponse(byteArrayOf(0x04, 0, 7, 9), 0x9000) }.getConfiguration()
        assertTrue(modern.signByHashAllowed)
        val legacy = TronApp { _, _ -> ApduResponse(byteArrayOf(0x04, 0, 7, 4), 0x9000) }.getConfiguration()
        assertTrue(!legacy.signByHashAllowed)
    }

    private fun channel(sent: MutableList<ByteArray>, signature: ByteArray) = ApduChannel { apdu, _ ->
        sent += apdu
        ApduResponse(signature, 0x9000)
    }

    private fun field(number: Int, payload: ByteArray): ByteArray {
        val tag = (number shl 3) or 2
        val tagBytes = varint(tag.toLong())
        return tagBytes + varint(payload.size.toLong()) + payload
    }

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val out = mutableListOf<Byte>()
        while (true) {
            if (remaining and 0x7fL.inv() == 0L) {
                out += remaining.toByte()
                return out.toByteArray()
            }
            out += ((remaining and 0x7f) or 0x80).toByte()
            remaining = remaining ushr 7
        }
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

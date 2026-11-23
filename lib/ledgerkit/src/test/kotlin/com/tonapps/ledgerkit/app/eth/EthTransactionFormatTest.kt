package com.tonapps.ledgerkit.app.eth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EthTransactionFormatTest {

    @Test
    fun `legacy detection uses rlp list prefix`() {
        assertTrue(EthTransactionFormat.isLegacy(byteArrayOf(0xf8.toByte(), 0x6b)))
        assertTrue(EthTransactionFormat.isLegacy(byteArrayOf(0xc0.toByte())))
        assertFalse(EthTransactionFormat.isLegacy(byteArrayOf(0x02, 0xf8.toByte())))
        assertFalse(EthTransactionFormat.isLegacy(byteArrayOf(0x01)))
    }

    @Test
    fun `legacy chunk size never leaves only the chain id trailer in the last chunk`() {
        val pathLength = 21
        for (chainId in listOf(1L, 56L, 137L, 324L, 8453L, 42161L, 43114L)) {
            val trailer = EthTransactionFormat.rlpLengthOfUnsigned(chainId) + 2
            for (total in 1..2000) {
                val size = EthTransactionFormat.legacySafeChunkSize(total, pathLength, chainId)
                val remainder = total % size
                assertTrue("chainId=$chainId total=$total size=$size", remainder == 0 || remainder > trailer || total <= size)
                assertTrue(size in (pathLength + 1)..255)
            }
        }
    }

    @Test
    fun `short legacy payload keeps full chunk size`() {
        assertEquals(255, EthTransactionFormat.legacySafeChunkSize(200, 21, 56))
    }

    @Test
    fun `legacy parity recovery follows eip155`() {
        assertEquals(0, EthTransactionFormat.legacyParity(37, 1))
        assertEquals(1, EthTransactionFormat.legacyParity(38, 1))
        assertEquals(0, EthTransactionFormat.legacyParity((56 * 2 + 35) and 0xff, 56))
        assertEquals(1, EthTransactionFormat.legacyParity((56 * 2 + 36) and 0xff, 56))
        assertEquals(0, EthTransactionFormat.legacyParity(((42161L * 2 + 35) and 0xff).toInt(), 42161))
        assertEquals(1, EthTransactionFormat.legacyParity(((42161L * 2 + 36) and 0xff).toInt(), 42161))
        assertEquals(1, EthTransactionFormat.legacyParity(0, 110))
    }

    @Test
    fun `rlp length of unsigned integers`() {
        assertEquals(1, EthTransactionFormat.rlpLengthOfUnsigned(1))
        assertEquals(1, EthTransactionFormat.rlpLengthOfUnsigned(0x7f))
        assertEquals(2, EthTransactionFormat.rlpLengthOfUnsigned(0x80))
        assertEquals(2, EthTransactionFormat.rlpLengthOfUnsigned(0xff))
        assertEquals(3, EthTransactionFormat.rlpLengthOfUnsigned(42161))
        assertEquals(3, EthTransactionFormat.rlpLengthOfUnsigned(8453))
    }

    @Test
    fun `address derives from uncompressed public key`() {
        val publicKey = hex(
            "04" +
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
        )
        assertEquals("7e5f4552091a69125d5dfcb7b8c2659029395bdf", EthApp.addressFromPublicKey(publicKey))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

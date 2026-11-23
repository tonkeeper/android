package com.tonapps.blockchain.evm

import com.tonapps.chainkit.core.chain.model.account.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class EvmTypedDataTest {

    @Test
    fun `plain typed data is addressed to the chain of its domain`() {
        val typedData = EvmTypedData.decode(typedData(chainId = "8453"))!!

        assertTrue(typedData.isAddressedTo(Chain.Base.Mainnet))
        assertFalse(typedData.isAddressedTo(Chain.Ethereum.Mainnet))
    }

    @Test
    fun `hex encoded typed data is decoded before its chain is read`() {
        val typedData = EvmTypedData.decode(hex(typedData(chainId = "1")))!!

        assertEquals(BigInteger.ONE, typedData.chainId)
        assertFalse(typedData.isAddressedTo(Chain.Base.Mainnet))
        assertEquals(typedData(chainId = "1"), typedData.json)
    }

    @Test
    fun `a chain id is read as a number, a decimal string or a hex string`() {
        listOf("8453", "\"8453\"", "\"0x2105\"", "\"0x0000000000002105\"").forEach {
            assertEquals(it, BigInteger.valueOf(8453), EvmTypedData.decode(typedData(chainId = it))?.chainId)
        }
        listOf("\"0xa4b1\"", "\"0xA4B1\"").forEach {
            assertEquals(it, BigInteger.valueOf(42161), EvmTypedData.decode(typedData(chainId = it))?.chainId)
        }
    }

    @Test
    fun `typed data without a chain id is addressed to any chain`() {
        val typedData = EvmTypedData.decode("""{"domain":{"name":"Test"},"message":{}}""")!!

        assertNull(typedData.chainId)
        assertTrue(typedData.isAddressedTo(Chain.Base.Mainnet))
    }

    @Test
    fun `a chain id declared by the domain type but left without a value is refused`() {
        val types = """"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"chainId","type":"uint256"}]}"""

        assertNull(EvmTypedData.decode("""{$types,"domain":{"name":"Test"},"message":{}}"""))
        assertNull(EvmTypedData.decode("""{$types,"domain":{"name":"Test","chainId":null},"message":{}}"""))
    }

    @Test
    fun `a chain id that cannot be read as a uint256 is refused`() {
        listOf(
            "\"0x\"",
            "\"0xzz\"",
            "\"0X2105\"",
            "\" 8453\"",
            "\"08453\"",
            "\"-8453\"",
            "\"+8453\"",
            "\"\"",
            "\"٨٤٥٣\"",
            "8453.0",
            "8.453e3",
            "-1",
            "true",
            "{\"value\":8453}",
            "[8453]",
            "\"0x1" + "0".repeat(64) + "\"",
        ).forEach {
            assertNull(it, EvmTypedData.decode(typedData(chainId = it)))
        }
    }

    @Test
    fun `typed data that cannot be read is refused`() {
        listOf(
            "",
            "not json",
            "[]",
            "\"text\"",
            """{"domain":"Test","message":{}}""",
            """{"domain":[],"message":{}}""",
            "0x",
            "0x7",
            "0xzz",
            "0xff",
            hex("not json"),
            hex(typedData(chainId = "1")).uppercase(),
        ).forEach {
            assertNull(it, EvmTypedData.decode(it))
        }
    }

    @Test
    fun `repeated keys are signed as they were read`() {
        val typedData = EvmTypedData.decode("""{"domain":{"chainId":8453,"chainId":1},"message":{}}""")!!

        assertEquals(BigInteger.ONE, typedData.chainId)
        assertEquals("""{"domain":{"chainId":1},"message":{}}""", typedData.json)
    }

    private fun typedData(chainId: String): String {
        return """{"domain":{"name":"Test","chainId":$chainId},"message":{}}"""
    }

    private fun hex(value: String): String {
        return "0x" + value.encodeToByteArray().joinToString(separator = "") { "%02x".format(it) }
    }
}

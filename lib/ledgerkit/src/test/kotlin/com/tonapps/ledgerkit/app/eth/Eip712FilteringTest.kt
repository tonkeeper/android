package com.tonapps.ledgerkit.app.eth

import org.junit.Assert.assertEquals
import org.junit.Test

class Eip712FilteringTest {

    private val permit = Eip712TypedData.parse(
        """
        {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
        "Permit":[{"name":"owner","type":"address"},{"name":"spender","type":"address"},{"name":"value","type":"uint256"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]},
        "primaryType":"Permit","domain":{"name":"USD Coin","version":"2","chainId":1,"verifyingContract":"0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"},
        "message":{"owner":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","spender":"0x1111111254EEB25477B68fb85Ed929f73A960582","value":"1000000","nonce":"0","deadline":"1893456000"}}
        """.trimIndent(),
    )

    private val permitFilters = Eip712Filters(
        messageName = "Permit USDC transfers",
        messageSignature = byteArrayOf(0x30, 0x01),
        fields = listOf(
            Eip712FieldFilter("owner", Eip712FieldFormat.Raw, "From", byteArrayOf(0x31)),
            Eip712FieldFilter("spender", Eip712FieldFormat.Raw, "Approve to spender", byteArrayOf(0x32)),
            Eip712FieldFilter("value", Eip712FieldFormat.Amount, "Amount allowance", byteArrayOf(0x33), coinRef = 0xff),
            Eip712FieldFilter("deadline", Eip712FieldFormat.DateTime, "Approval expire", byteArrayOf(0x34)),
        ),
    )

    @Test
    fun `schema hash matches the ledger cal key of an erc 2612 permit`() {
        assertEquals("d4dd8410bdcf861c48d353f8e3a9b738282a0fd9ba7239f59baa9099", hex(permit.schemaHash()))
        assertEquals(1L, permit.chainId)
        assertEquals("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", permit.verifyingContract)
    }

    @Test
    fun `filtered permit activates filtering, provides the token before its amount and labels each shown field`() {
        val token = byteArrayOf(0x04, 0x55)
        val tokens = mapOf("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" to token)
        val commands = Eip712LedgerEncoder(permit, Eip712ClearSigning(permitFilters, tokens))
            .structImplementationCommands()
            .map(::hex)
        val messageRoot = commands.indexOf("e01c000006" + hex("Permit".toByteArray()))
        assertEquals("e01e000000", commands[0])
        assertEquals("e01c00000c" + hex("EIP712Domain".toByteArray()), commands[1])
        assertEquals(
            "e01e000f" + "1a" + "15" + hex("Permit USDC transfers".toByteArray()) + "04" + "02" + "3001",
            commands[messageRoot - 1],
        )
        assertEquals(
            listOf(
                "e01e00ff" + "07" + "04" + hex("From".toByteArray()) + "01" + "31",
                "e01c00ff16" + "0014" + "5aaeb6053f3e94c9b9a09f33669435e7ef1beaed",
                "e01e00ff" + "15" + "12" + hex("Approve to spender".toByteArray()) + "01" + "32",
                "e01c00ff16" + "0014" + "1111111254eeb25477b68fb85ed929f73a960582",
                "e00a000002" + "0455",
                "e01e00fe" + "14" + "10" + hex("Amount allowance".toByteArray()) + "ff" + "01" + "33",
                "e01c00ff05" + "0003" + "0f4240",
                "e01c00ff03" + "0001" + "00",
                "e01e00fc" + "12" + "0f" + hex("Approval expire".toByteArray()) + "01" + "34",
                "e01c00ff06" + "0004" + "70dbd880",
            ),
            commands.drop(messageRoot + 1),
        )
    }

    @Test
    fun `filters inside an empty array are discarded with their full path`() {
        val order = Eip712TypedData.parse(
            """
            {"types":{"EIP712Domain":[{"name":"chainId","type":"uint256"}],"Output":[{"name":"token","type":"address"},{"name":"amount","type":"uint256"}],
            "Order":[{"name":"outputs","type":"Output[]"},{"name":"memo","type":"string"}]},
            "primaryType":"Order","domain":{"chainId":1},"message":{"outputs":[],"memo":"hi"}}
            """.trimIndent(),
        )
        val filters = Eip712Filters(
            messageName = "Order",
            messageSignature = byteArrayOf(0x30),
            fields = listOf(
                Eip712FieldFilter("outputs.[].token", Eip712FieldFormat.Token, "Token", byteArrayOf(0x41), coinRef = 1),
                Eip712FieldFilter("outputs.[].amount", Eip712FieldFormat.Amount, "Amount", byteArrayOf(0x42), coinRef = 1),
                Eip712FieldFilter("memo", Eip712FieldFormat.Raw, "Memo", byteArrayOf(0x43)),
            ),
        )
        val commands = Eip712LedgerEncoder(order, Eip712ClearSigning(filters, emptyMap())).structImplementationCommands().map(::hex)
        val array = commands.indexOf("e01c000f0100")
        assertEquals(
            listOf(
                "e01e0001" + "11" + "10" + hex("outputs.[].token".toByteArray()),
                "e01e01fd" + "03" + "01" + "01" + "41",
                "e01e0001" + "12" + "11" + hex("outputs.[].amount".toByteArray()),
                "e01e01fe" + "0a" + "06" + hex("Amount".toByteArray()) + "01" + "01" + "42",
                "e01e00ff" + "07" + "04" + hex("Memo".toByteArray()) + "01" + "43",
                "e01c00ff04" + "0002" + hex("hi".toByteArray()),
            ),
            commands.drop(array + 1),
        )
    }

    @Test
    fun `every coin ref gets its own token slot even when two refs name the same token`() {
        val order = Eip712TypedData.parse(
            """
            {"types":{"EIP712Domain":[{"name":"chainId","type":"uint256"}],"Order":[{"name":"input","type":"address"},{"name":"inputAmount","type":"uint256"},{"name":"output","type":"address"},{"name":"outputAmount","type":"uint256"}]},
            "primaryType":"Order","domain":{"chainId":1},
            "message":{"input":"0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","inputAmount":"1","output":"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","outputAmount":"2"}}
            """.trimIndent(),
        )
        val filters = Eip712Filters(
            messageName = "Order",
            messageSignature = byteArrayOf(0x30),
            fields = listOf(
                Eip712FieldFilter("input", Eip712FieldFormat.Token, "In", byteArrayOf(0x41), coinRef = 0),
                Eip712FieldFilter("inputAmount", Eip712FieldFormat.Amount, "In", byteArrayOf(0x42), coinRef = 0),
                Eip712FieldFilter("output", Eip712FieldFormat.Token, "Out", byteArrayOf(0x43), coinRef = 1),
                Eip712FieldFilter("outputAmount", Eip712FieldFormat.Amount, "Out", byteArrayOf(0x44), coinRef = 1),
            ),
        )
        val tokens = mapOf("0x" + "a".repeat(40) to byteArrayOf(0x07))
        val commands = Eip712LedgerEncoder(order, Eip712ClearSigning(filters, tokens)).structImplementationCommands().map(::hex)
        val provides = commands.withIndex().filter { it.value == "e00a00000107" }.map { it.index }
        assertEquals(2, provides.size)
        assertEquals("e01e00fd" + "03" + "00" + "01" + "41", commands[provides[0] + 1])
        assertEquals("e01e00fd" + "03" + "01" + "01" + "43", commands[provides[1] + 1])
    }

    @Test
    fun `token contracts come from token fields and the verifying contract of a permit amount`() {
        val order = Eip712TypedData.parse(
            """
            {"types":{"EIP712Domain":[{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "Output":[{"name":"token","type":"address"}],"Order":[{"name":"input","type":"address"},{"name":"outputs","type":"Output[]"}]},
            "primaryType":"Order","domain":{"chainId":"0x2105","verifyingContract":"0x000000000022D473030F116dDEE9F6B43aC78BA3"},
            "message":{"input":"0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","outputs":[{"token":"0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"token":"0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}]}}
            """.trimIndent(),
        )
        val filters = Eip712Filters(
            messageName = "Order",
            messageSignature = byteArrayOf(0x30),
            fields = listOf(
                Eip712FieldFilter("input", Eip712FieldFormat.Token, "In", byteArrayOf(0x41), coinRef = 0),
                Eip712FieldFilter("outputs.[].token", Eip712FieldFormat.Token, "Out", byteArrayOf(0x42), coinRef = 1),
                Eip712FieldFilter("fee", Eip712FieldFormat.Amount, "Fee", byteArrayOf(0x43), coinRef = 0xff),
            ),
        )
        assertEquals(8453L, order.chainId)
        assertEquals(
            listOf(
                "0x" + "a".repeat(40),
                "0x" + "b".repeat(40),
                "0x000000000022d473030f116ddee9f6b43ac78ba3",
            ),
            filters.tokenContracts(order),
        )
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

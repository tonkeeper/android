package com.tonapps.ledgerkit.app.eth

import org.junit.Assert.assertEquals
import org.junit.Test

class Eip712LedgerEncoderTest {

    private val etherMail = """
        {
          "types": {
            "EIP712Domain": [
              {"name": "name", "type": "string"},
              {"name": "version", "type": "string"},
              {"name": "chainId", "type": "uint256"},
              {"name": "verifyingContract", "type": "address"}
            ],
            "Person": [
              {"name": "name", "type": "string"},
              {"name": "wallet", "type": "address"}
            ],
            "Mail": [
              {"name": "from", "type": "Person"},
              {"name": "to", "type": "Person"},
              {"name": "contents", "type": "string"}
            ]
          },
          "primaryType": "Mail",
          "domain": {
            "name": "Ether Mail",
            "version": "1",
            "chainId": 1,
            "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
          },
          "message": {
            "contents": "Hello, Bob!",
            "from": {"name": "Cow", "wallet": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},
            "to": {"name": "Bob", "wallet": "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"}
          }
        }
    """.trimIndent()

    @Test
    fun `struct definitions are sent in alphabetical type order with the ledger type descriptor layout`() {
        val commands = Eip712LedgerEncoder(Eip712TypedData.parse(etherMail)).structDefinitionCommands().map(::hex)
        assertEquals(
            listOf(
                "e01a00000c" + hex("EIP712Domain".toByteArray()),
                "e01a00ff06" + "05" + "04" + hex("name".toByteArray()),
                "e01a00ff09" + "05" + "07" + hex("version".toByteArray()),
                "e01a00ff0a" + "42" + "20" + "07" + hex("chainId".toByteArray()),
                "e01a00ff13" + "03" + "11" + hex("verifyingContract".toByteArray()),
                "e01a000004" + hex("Mail".toByteArray()),
                "e01a00ff0d" + "00" + "06" + hex("Person".toByteArray()) + "04" + hex("from".toByteArray()),
                "e01a00ff0b" + "00" + "06" + hex("Person".toByteArray()) + "02" + hex("to".toByteArray()),
                "e01a00ff0a" + "05" + "08" + hex("contents".toByteArray()),
                "e01a000006" + hex("Person".toByteArray()),
                "e01a00ff06" + "05" + "04" + hex("name".toByteArray()),
                "e01a00ff08" + "03" + "06" + hex("wallet".toByteArray()),
            ),
            commands,
        )
    }

    @Test
    fun `struct implementation walks fields in type order`() {
        val commands = Eip712LedgerEncoder(Eip712TypedData.parse(etherMail)).structImplementationCommands().map(::hex)
        assertEquals(
            listOf(
                "e01c00000c" + hex("EIP712Domain".toByteArray()),
                "e01c00ff0c" + "000a" + hex("Ether Mail".toByteArray()),
                "e01c00ff03" + "0001" + hex("1".toByteArray()),
                "e01c00ff03" + "0001" + "01",
                "e01c00ff16" + "0014" + "cccccccccccccccccccccccccccccccccccccccc",
                "e01c000004" + hex("Mail".toByteArray()),
                "e01c00ff05" + "0003" + hex("Cow".toByteArray()),
                "e01c00ff16" + "0014" + "cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                "e01c00ff05" + "0003" + hex("Bob".toByteArray()),
                "e01c00ff16" + "0014" + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "e01c00ff0d" + "000b" + hex("Hello, Bob!".toByteArray()),
            ),
            commands,
        )
    }

    @Test
    fun `arrays negative integers and long strings`() {
        val typedData = Eip712TypedData.parse(
            """
            {
              "types": {
                "EIP712Domain": [{"name": "chainId", "type": "uint256"}],
                "Order": [
                  {"name": "amounts", "type": "int32[]"},
                  {"name": "tags", "type": "bytes4[2]"},
                  {"name": "memo", "type": "string"}
                ]
              },
              "primaryType": "Order",
              "domain": {"chainId": "0x2105"},
              "message": {"amounts": [-1, "5"], "tags": ["0x01020304", "0xaabbccdd"], "memo": "${"x".repeat(300)}"}
            }
            """.trimIndent(),
        )
        val encoder = Eip712LedgerEncoder(typedData)
        val definitions = encoder.structDefinitionCommands().map(::hex)
        assertEquals("e01a00ff0c" + "c1" + "04" + "01" + "00" + "07" + hex("amounts".toByteArray()), definitions[3])
        assertEquals("e01a00ff0a" + "c6" + "04" + "01" + "01" + "02" + "04" + hex("tags".toByteArray()), definitions[4])
        val implementation = encoder.structImplementationCommands().map(::hex)
        assertEquals("e01c00ff04" + "0002" + "2105", implementation[1])
        assertEquals("e01c000f01" + "02", implementation[3])
        assertEquals("e01c00ff06" + "0004" + "ffffffff", implementation[4])
        assertEquals("e01c00ff03" + "0001" + "05", implementation[5])
        assertEquals("e01c000f01" + "02", implementation[6])
        assertEquals("e01c00ff06" + "0004" + "01020304", implementation[7])
        assertEquals("e01c00ff06" + "0004" + "aabbccdd", implementation[8])
        val memoFirst = implementation[9]
        val memoLast = implementation[10]
        assertEquals("e01c01ffff" + "012c", memoFirst.substring(0, 14))
        assertEquals(10 + 255 * 2, memoFirst.length)
        assertEquals("e01c00ff2f", memoLast.substring(0, 10))
        assertEquals(11, implementation.size)
    }

    @Test
    fun `missing domain type is inferred from the domain object`() {
        val typedData = Eip712TypedData.parse(
            """
            {"types": {"Ping": [{"name": "n", "type": "uint8"}]}, "primaryType": "Ping",
             "domain": {"name": "App", "chainId": 56}, "message": {"n": 7}}
            """.trimIndent(),
        )
        assertEquals(listOf(Eip712Field("name", "string"), Eip712Field("chainId", "uint256")), typedData.domainFields)
        assertEquals("EIP712Domain", typedData.types.keys.first())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `matches the ledgerjs golden fixture for mail with address arrays`() {
        val typedData = Eip712TypedData.parse(
            """
            {
              "domain": {"chainId": 5, "name": "Ether Mail", "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC", "version": "1"},
              "message": {
                "contents": "Hello, Bob!",
                "from": {"name": "Cow", "wallets": ["0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826", "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"]},
                "to": {"name": "Bob", "wallets": ["0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB", "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57", "0xB0B0b0b0b0b0B000000000000000000000000000"]}
              },
              "primaryType": "Mail",
              "types": {
                "EIP712Domain": [
                  {"name": "name", "type": "string"},
                  {"name": "version", "type": "string"},
                  {"name": "chainId", "type": "uint256"},
                  {"name": "verifyingContract", "type": "address"}
                ],
                "Mail": [
                  {"name": "from", "type": "Person"},
                  {"name": "to", "type": "Person"},
                  {"name": "contents", "type": "string"}
                ],
                "Person": [
                  {"name": "name", "type": "string"},
                  {"name": "wallets", "type": "address[]"}
                ]
              }
            }
            """.trimIndent(),
        )
        val encoder = Eip712LedgerEncoder(typedData)
        val expected = listOf(
            "e01a00000c454950373132446f6d61696e",
            "e01a00ff0605046e616d65",
            "e01a00ff09050776657273696f6e",
            "e01a00ff0a422007636861696e4964",
            "e01a00ff130311766572696679696e67436f6e7472616374",
            "e01a0000044d61696c",
            "e01a00ff0d0006506572736f6e0466726f6d",
            "e01a00ff0b0006506572736f6e02746f",
            "e01a00ff0a0508636f6e74656e7473",
            "e01a000006506572736f6e",
            "e01a00ff0605046e616d65",
            "e01a00ff0b830100077761" + "6c6c657473",
            "e01c00000c454950373132446f6d61696e",
            "e01c00ff0c000a4574686572204d61696c",
            "e01c00ff03000131",
            "e01c00ff03000105",
            "e01c00ff160014cccccccccccccccccccccccccccccccccccccccc",
            "e01c0000044d61696c",
            "e01c00ff050003436f77",
            "e01c000f0102",
            "e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
            "e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
            "e01c00ff050003426f62",
            "e01c000f0103",
            "e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57",
            "e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000",
            "e01c00ff0d000b48656c6c6f2c20426f6221",
        )
        val actual = (encoder.structDefinitionCommands() + encoder.structImplementationCommands()).map(::hex)
        assertEquals(expected, actual)
    }

    @Test
    fun `nested arrays open the outermost level first`() {
        val typedData = Eip712TypedData.parse(
            """
            {"types": {"EIP712Domain": [], "Grid": [{"name": "cells", "type": "uint8[2][]"}]}, "primaryType": "Grid",
             "domain": {}, "message": {"cells": [[1, 2], [3, 4], [5, 6]]}}
            """.trimIndent(),
        )
        val implementation = Eip712LedgerEncoder(typedData).structImplementationCommands().map(::hex)
        assertEquals("e01c000f0103", implementation[2])
        assertEquals("e01c000f0102", implementation[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bare uint is rejected`() {
        Eip712LedgerEncoder(
            Eip712TypedData.parse(
                """{"types": {"EIP712Domain": [], "A": [{"name": "x", "type": "uint"}]}, "primaryType": "A", "domain": {}, "message": {"x": 1}}""",
            ),
        ).structDefinitionCommands()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range signed integer is rejected`() {
        Eip712LedgerEncoder(
            Eip712TypedData.parse(
                """{"types": {"EIP712Domain": [], "A": [{"name": "x", "type": "int8"}]}, "primaryType": "A", "domain": {}, "message": {"x": 128}}""",
            ),
        ).structImplementationCommands()
    }

    @Test
    fun `negative integers use full width twos complement`() {
        val implementation = Eip712LedgerEncoder(
            Eip712TypedData.parse(
                """{"types": {"EIP712Domain": [], "A": [{"name": "x", "type": "int16"}, {"name": "y", "type": "int256"}]}, "primaryType": "A", "domain": {}, "message": {"x": -128, "y": "-1"}}""",
            ),
        ).structImplementationCommands().map(::hex)
        assertEquals("e01c00ff040002ff80", implementation[2])
        assertEquals("e01c00ff220020" + "ff".repeat(32), implementation[3])
    }
}

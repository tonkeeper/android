package com.tonapps.ledgerkit.app.eth

import org.junit.Assert.assertEquals
import org.junit.Test

class Eip712HasherTest {

    private val specExample = Eip712TypedData.parse(
        """
        {
          "types": {
            "EIP712Domain": [
              {"name": "name", "type": "string"},
              {"name": "version", "type": "string"},
              {"name": "chainId", "type": "uint256"},
              {"name": "verifyingContract", "type": "address"}
            ],
            "Person": [{"name": "name", "type": "string"}, {"name": "wallet", "type": "address"}],
            "Mail": [{"name": "from", "type": "Person"}, {"name": "to", "type": "Person"}, {"name": "contents", "type": "string"}]
          },
          "primaryType": "Mail",
          "domain": {"name": "Ether Mail", "version": "1", "chainId": 1, "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"},
          "message": {
            "from": {"name": "Cow", "wallet": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},
            "to": {"name": "Bob", "wallet": "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"},
            "contents": "Hello, Bob!"
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `encode type lists dependencies alphabetically`() {
        assertEquals("Mail(Person from,Person to,string contents)Person(string name,address wallet)", Eip712Hasher.encodeType(specExample, "Mail"))
    }

    @Test
    fun `hashes match the eip712 specification example`() {
        assertEquals("f2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f", hex(Eip712Hasher.domainSeparator(specExample)))
        assertEquals("c52c0ee5d84264471806290a3f2c4cecfc5490626bf912d01f240d7a274b371e", hex(Eip712Hasher.messageHash(specExample)))
        assertEquals("be609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2", hex(Eip712Hasher.digest(specExample)))
    }

    @Test
    fun `domain without verifying contract is supported`() {
        val typedData = Eip712TypedData.parse(
            """{"types": {"Link": [{"name": "account", "type": "address"}]}, "primaryType": "Link", "domain": {"name": "Perps", "chainId": 42161}, "message": {"account": "0x0000000000000000000000000000000000000001"}}""",
        )
        assertEquals(32, Eip712Hasher.domainSeparator(typedData).size)
        assertEquals("EIP712Domain(string name,uint256 chainId)", Eip712Hasher.encodeType(typedData, "EIP712Domain"))
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.app.eth.Eip712FieldFormat
import com.tonapps.ledgerkit.app.eth.Eip712TypedData
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvmTypedDataFiltersTest {

    private val filters = EvmTypedDataFilters(mockk())

    @Test
    fun `bundled ledger filters describe a usdc permit on ethereum`() {
        val found = requireNotNull(filters.find(permit(chainId = 1, token = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")))
        assertEquals("Permit USDC transfers", found.messageName)
        assertEquals(
            listOf(
                "owner" to Eip712FieldFormat.Raw,
                "spender" to Eip712FieldFormat.Raw,
                "value" to Eip712FieldFormat.Amount,
                "deadline" to Eip712FieldFormat.DateTime,
            ),
            found.fields.map { it.path to it.format },
        )
        assertEquals(0xff, found.fields.single { it.path == "value" }.coinRef)
    }

    @Test
    fun `a permit the bundled filters do not know is left without filters`() {
        assertNull(filters.find(permit(chainId = 8453, token = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")))
    }

    @Test
    fun `an entry with a format the device does not know is ignored`() {
        val key = "1:0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48:d4dd8410bdcf861c48d353f8e3a9b738282a0fd9ba7239f59baa9099"
        val catalog = """{"$key":{"contractName":{"label":"Permit","signature":"30"},"fields":[{"format":"calldata","label":"X","path":"value","signature":"31"}]}}"""
        val custom = EvmTypedDataFilters(mockk(), catalog = { catalog })
        assertNull(custom.find(permit(chainId = 1, token = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")))
    }

    private fun permit(chainId: Long, token: String) = Eip712TypedData.parse(
        """
        {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
        "Permit":[{"name":"owner","type":"address"},{"name":"spender","type":"address"},{"name":"value","type":"uint256"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]},
        "primaryType":"Permit","domain":{"name":"USD Coin","version":"2","chainId":$chainId,"verifyingContract":"$token"},
        "message":{"owner":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","spender":"0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2","value":"2000000","nonce":"0","deadline":"1893456000"}}
        """.trimIndent(),
    )
}

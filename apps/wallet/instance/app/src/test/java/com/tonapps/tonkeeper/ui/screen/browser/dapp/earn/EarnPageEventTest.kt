package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EarnPageEventTest {

    @Test
    fun anAddressOrATransactionHashNeverLeavesThePage() {
        val params = EarnPageEvent.params(
            mapOf(
                "error_message" to "execution reverted for 0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
                "url" to "https://basescan.org/tx/" + "ab".repeat(32),
            )
        )

        assertEquals("execution reverted for <hex>", params["error_message"])
        assertEquals("https://basescan.org/tx/<hex>", params["url"])
    }

    @Test
    fun aVaultKeepsItsAssetId() {
        val vault = "base/mainnet/erc20/0xc1256ae5ff1cf2719d4937adb3bbccab2e00a2ca"

        val params = EarnPageEvent.params(mapOf("vault_id" to vault))

        assertEquals(vault, params["vault_id"])
    }

    @Test
    fun thePageCannotPassItsEventsOffAsTheWallets() {
        val params = EarnPageEvent.params(mapOf("isExternalWebView" to 0, "eventProvider" to "tonkeeper"))

        assertEquals(1, params["isExternalWebView"])
        assertEquals("vaults.fyi", params["eventProvider"])
    }

    // Aptabase rejects the whole event over one key that is too long.
    @Test
    fun aKeyAptabaseWouldRejectIsDropped() {
        val params = EarnPageEvent.params(mapOf("k".repeat(41) to "value", "amount_usd" to 69))

        assertFalse(params.containsKey("k".repeat(41)))
        assertEquals(69, params["amount_usd"])
    }
}
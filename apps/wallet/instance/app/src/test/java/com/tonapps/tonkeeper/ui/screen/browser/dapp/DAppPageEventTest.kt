package com.tonapps.tonkeeper.ui.screen.browser.dapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DAppPageEventTest {

    @Test
    fun anAddressOrATransactionHashNeverLeavesThePage() {
        val params = DAppPageEvent.params(
            mapOf(
                "error_message" to "execution reverted for 0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
                "url" to "https://basescan.org/tx/" + "ab".repeat(32),
            ),
            PROVIDER,
        )

        assertEquals("execution reverted for <hex>", params["error_message"])
        assertEquals("https://basescan.org/tx/<hex>", params["url"])
    }

    @Test
    fun anAddressOfAnyChainNeverLeavesThePage() {
        val params = DAppPageEvent.params(
            mapOf(
                "wallet" to "EQD8CPSozp5MN3x8smu6aUjBatSlDRNuzzQQ_HLbPP7ACyfj",
                "digitFree" to "UQAYbigAGcESXjUBxp_IObJT_GhoXvTqViGKdNqgeTjcIkVq",
                "to" to "sent to EQD8CPSozp5MN3x8smu6aUjBatSlDRNuzzQQ_HLbPP7ACyfj today",
                "testnet" to "kQDAIGpoaHGTKhQqF_unOsZBbgMYxT4AwJo3O6PDPQE2qX2q",
                "masterchain" to "Ef-XW6P2luwm_5HE_rZPVPJQ79R1DkiMY02l4AtuWXJCi2iS",
                "standardBase64" to "EQDew/9ZUqEmctrWJmefNJDg5iM+0XsCLrJrgGFC7NZYasCR",
                "gluedPrefix" to "wallet_EQDew/9ZUqEmctrWJmefNJDg5iM+0XsCLrJrgGFC7NZYasCR",
                "formEncoded" to "q=send+EQDew/9ZUqEmctrWJmefNJDg5iM+0XsCLrJrgGFC7NZYasCR&x=1",
                "suffixed" to "UQAYbigAGcESXjUBxp_IObJT_GhoXvTqViGKdNqgeTjcIkVq_20240101",
                "tonviewer" to "https://tonviewer.com/EQD8CPSozp5MN3x8smu6aUjBatSlDRNuzzQQ_HLbPP7ACyfj/jetton",
                "raw" to "0:27bafbc62e01aedd3fc7261a53b46a4bf314f202ece4503c7306e347525100f3",
                "tron" to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                "solana" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "longId" to "swap_confirmation_screen_variant_2",
            ),
            PROVIDER,
        )

        assertEquals("<hex>", params["wallet"])
        assertEquals("<hex>", params["digitFree"])
        assertEquals("sent to <hex> today", params["to"])
        assertEquals("<hex>", params["testnet"])
        assertEquals("<hex>", params["masterchain"])
        assertEquals("<hex>", params["standardBase64"])
        assertEquals("wallet_<hex>", params["gluedPrefix"])
        assertEquals("q=send+<hex>&x=1", params["formEncoded"])
        assertEquals("<hex>_20240101", params["suffixed"])
        assertEquals("https://tonviewer.com/<hex>/jetton", params["tonviewer"])
        assertEquals("0:<hex>", params["raw"])
        assertEquals("<hex>", params["tron"])
        assertEquals("<hex>", params["solana"])
        assertEquals("<hex>", params["longId"])
    }

    @Test
    fun textThatOnlyLooksLikeAnAddressStays() {
        val values = listOf(
            "the_workflow_for_onboarding_screens_and_more_steps_here",
            "https://docs.example.com/guides/Efficient-staking/v2/how-to-deposit-ton-and-earn-more",
            "EQD8CPSozp5MN3x8smu6aUjB/tSlDRNuzzQQ_HLbPP7ACyfj",
            "carousel_banner_v2",
        )

        val params = DAppPageEvent.params(values.associateBy { "k${values.indexOf(it)}" }, PROVIDER)

        values.forEachIndexed { index, value -> assertEquals(value, params["k$index"]) }
    }

    @Test
    fun aValueTooLongToMaskIsReplacedWithAMarker() {
        val params = DAppPageEvent.params(mapOf("blob" to "z".repeat(1025), "note" to "z".repeat(1024)), PROVIDER)

        assertEquals("<too_long>", params["blob"])
        assertEquals("z".repeat(1024), params["note"])
    }

    @Test
    fun aJettonKeepsItsAssetId() {
        val jetton = "ton/mainnet/jetton/EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"

        val params = DAppPageEvent.params(mapOf("asset_id" to jetton), PROVIDER)

        assertEquals(jetton, params["asset_id"])
    }

    @Test
    fun aVaultKeepsItsAssetId() {
        val vault = "base/mainnet/erc20/0xc1256ae5ff1cf2719d4937adb3bbccab2e00a2ca"

        val params = DAppPageEvent.params(mapOf("vault_id" to vault), PROVIDER)

        assertEquals(vault, params["vault_id"])
    }

    @Test
    fun thePageCannotPassItsEventsOffAsTheWallets() {
        val params = DAppPageEvent.params(mapOf("isExternalWebView" to 0, "eventProvider" to "tonkeeper"), PROVIDER)

        assertEquals(1, params["isExternalWebView"])
        assertEquals(PROVIDER, params["eventProvider"])
    }

    // Aptabase rejects the whole event over one key that is too long.
    @Test
    fun aKeyAptabaseWouldRejectIsDropped() {
        val params = DAppPageEvent.params(mapOf("k".repeat(41) to "value", "amount_usd" to 69), PROVIDER)

        assertFalse(params.containsKey("k".repeat(41)))
        assertEquals(69, params["amount_usd"])
    }

    private companion object {
        private const val PROVIDER = "vaults.fyi"
    }
}

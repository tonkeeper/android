package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarnDAppAnalyticsTest {

    @Test
    fun aVaultPageNamesItsVaultByAssetId() {
        assertEquals(
            "eth/mainnet/erc20/0xabc",
            EarnDAppAnalytics.vaultId("https://earn.example/earn/mainnet/0xABC?ref=1#top"),
        )
        assertEquals("base/mainnet/erc20/0xabc", EarnDAppAnalytics.vaultId("https://earn.example/earn/base/0xabc"))
        assertEquals("arb/mainnet/erc20/0xabc", EarnDAppAnalytics.vaultId("https://earn.example/earn/arbitrum/0xabc"))
        assertEquals("bsc/mainnet/bep20/0xabc", EarnDAppAnalytics.vaultId("https://earn.example/earn/bsc/0xabc/"))
    }

    @Test
    fun anyOtherPageHasNoVault() {
        assertNull(EarnDAppAnalytics.vaultId(null))
        assertNull(EarnDAppAnalytics.vaultId("https://earn.example/earn"))
        assertNull(EarnDAppAnalytics.vaultId("https://earn.example/earn/mainnet"))
        assertNull(EarnDAppAnalytics.vaultId("https://earn.example/earn/mainnet/0xabc/deposit"))
        assertNull(EarnDAppAnalytics.vaultId("https://earn.example/earn/optimism/0xabc"))
        assertNull(EarnDAppAnalytics.vaultId("not a url"))
    }
}

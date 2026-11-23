package com.tonapps.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRouteTest {

    @Test
    fun appLinkAcceptsBothUniversalLinkDomains() {
        assertTrue(DeepLinkRoute.isAppLink("https://app.tonkeeper.com/staking"))
        assertTrue(DeepLinkRoute.isAppLink("https://app.keeperwallet.com/staking"))
        assertTrue(DeepLinkRoute.isAppLink("tonkeeper://staking"))
        assertTrue(DeepLinkRoute.isAppLink("keeper://staking"))
        assertTrue(DeepLinkRoute.isAppLink("ton://transfer/EQD"))
        assertFalse(DeepLinkRoute.isAppLink("https://keeperwallet.com/terms"))
        assertFalse(DeepLinkRoute.isAppLink("https://example.com/staking"))
    }

    @Test
    fun universalHostMatchesBothDomainsCaseInsensitively() {
        assertTrue(DeepLinkRoute.isUniversalHost("app.tonkeeper.com"))
        assertTrue(DeepLinkRoute.isUniversalHost("app.keeperwallet.com"))
        assertTrue(DeepLinkRoute.isUniversalHost("APP.KEEPERWALLET.COM"))
        assertFalse(DeepLinkRoute.isUniversalHost("keeperwallet.com"))
        assertFalse(DeepLinkRoute.isUniversalHost("go.tonkeeper.com"))
        assertFalse(DeepLinkRoute.isUniversalHost(null))
    }

    @Test
    fun referralCodeIsKeptTrimmed() {
        assertEquals("UQJS7K21w", DeepLinkRoute.Loyalty.referralCodeOf("UQJS7K21w"))
        assertEquals("UQJS7K21w", DeepLinkRoute.Loyalty.referralCodeOf("  UQJS7K21w "))
        assertEquals("a_b-C9", DeepLinkRoute.Loyalty.referralCodeOf("a_b-C9"))
    }

    @Test
    fun referralCodeOutsideTheCodeAlphabetIsDropped() {
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf(null))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf(""))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf("   "))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf("UQJS 7K21w"))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf("<script>"))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf("код"))
        assertNull(DeepLinkRoute.Loyalty.referralCodeOf("a".repeat(65)))
    }

    @Test
    fun referralPathKeepsOnlyASingleValidCode() {
        assertEquals(DeepLinkRoute.Loyalty("K7M2QX9P"), DeepLinkRoute.Loyalty.ofReferralPath(listOf("K7M2QX9P")))
        assertEquals(DeepLinkRoute.Loyalty(null), DeepLinkRoute.Loyalty.ofReferralPath(emptyList()))
        assertEquals(DeepLinkRoute.Loyalty(null), DeepLinkRoute.Loyalty.ofReferralPath(listOf("a.b")))
        assertEquals(DeepLinkRoute.Loyalty(null), DeepLinkRoute.Loyalty.ofReferralPath(listOf("Ab9", "extra")))
    }

    @Test
    fun earnLinkWithoutAVaultOpensTheCatalog() {
        assertEquals(DeepLinkRoute.Earn.Home, DeepLinkRoute.Earn.of(network = null, vaultId = null))
    }

    @Test
    fun earnLinkNamesItsVault() {
        assertEquals(
            DeepLinkRoute.Earn.Vault(network = "base", vaultId = EARN_VAULT_ID),
            DeepLinkRoute.Earn.of(network = "base", vaultId = EARN_VAULT_ID),
        )
        assertEquals(
            DeepLinkRoute.Earn.Vault(network = "bsc", vaultId = "$EARN_VAULT_ID-0"),
            DeepLinkRoute.Earn.of(network = "BSC", vaultId = "$EARN_VAULT_ID-0"),
        )
        assertEquals(
            DeepLinkRoute.Earn.Vault(network = "mainnet", vaultId = "$EARN_VAULT_ID-usdc-v2"),
            DeepLinkRoute.Earn.of(network = "mainnet", vaultId = "$EARN_VAULT_ID-usdc-v2"),
        )
    }

    @Test
    fun earnLinkOutsideTheEarnRoutesIsRejected() {
        val links = listOf(
            "optimism" to EARN_VAULT_ID,
            "eth" to EARN_VAULT_ID,
            "base" to "0xc1256Ae5",
            "base" to "$EARN_VAULT_ID-",
            "base" to "$EARN_VAULT_ID-a/../../swap",
            "base" to null,
            null to EARN_VAULT_ID,
        )
        for ((network, vaultId) in links) {
            assertThrows("$network/$vaultId", IllegalArgumentException::class.java) {
                DeepLinkRoute.Earn.of(network = network, vaultId = vaultId)
            }
        }
    }

    @Test
    fun perpsMarketLinkTakesAnIdOrATicker() {
        assertEquals(DeepLinkRoute.Perps.Market(10902), DeepLinkRoute.Perps.of(listOf("market", "10902"), entryPoint = null))
        assertEquals(DeepLinkRoute.Perps.AutoClose(7), DeepLinkRoute.Perps.of(listOf("market", "7", "auto-close"), entryPoint = null))
        assertEquals(DeepLinkRoute.Perps.MarketSymbol("ANT"), DeepLinkRoute.Perps.of(listOf("market", "ANT"), entryPoint = null))
        assertEquals(
            DeepLinkRoute.Perps.MarketSymbol("1000pepe"),
            DeepLinkRoute.Perps.of(listOf("market", "1000pepe"), entryPoint = null),
        )
    }

    @Test
    fun perpsMarketLinkWithAnInvalidMarketIsRejected() {
        val links = listOf(
            listOf("market", "-1"),
            listOf("market", "99999999999"),
            listOf("market", "ANT", "auto-close"),
            listOf("market", "BTC-USD"),
            listOf("market", "ÄNT"),
            listOf("market", ""),
        )
        for (segments in links) {
            assertThrows(segments.joinToString("/"), IllegalArgumentException::class.java) {
                DeepLinkRoute.Perps.of(segments, entryPoint = null)
            }
        }
    }

    private companion object {
        const val EARN_VAULT_ID = "0xc1256Ae5FF1cf2719D4937adb3bbCCab2E00A2Ca"
    }
}

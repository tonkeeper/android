package com.tonapps.wallet.data.attribution.appsflyer

import com.tonapps.bus.core.contract.UtmParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsFlyerDeepLinkTest {

    private fun appLinkOf(vararg values: Pair<String, Any?>): String {
        return appsFlyerAppLink(appsFlyerValuesOf(values.toMap()))
    }

    @Test
    fun loyaltyLinkCarriesTheReferralCode() {
        assertEquals("tonkeeper://loyalty?referral_code=UQJS7K21w", appLinkOf("deep_link_value" to "loyalty", "deep_link_sub1" to "UQJS7K21w"))
        assertEquals("tonkeeper://loyalty?referral_code=UQJS7K21w", appLinkOf("deep_link_value" to " Loyalty ", "deep_link_sub1" to " UQJS7K21w "))
    }

    @Test
    fun loyaltyLinkWithoutACodeOpensTheSection() {
        assertEquals("tonkeeper://loyalty", appLinkOf("deep_link_value" to "loyalty"))
        assertEquals("tonkeeper://loyalty", appLinkOf("deep_link_value" to "loyalty", "deep_link_sub1" to "  "))
    }

    @Test
    fun theCodeIsEncodedIntoTheQuery() {
        assertEquals("tonkeeper://loyalty?referral_code=a%26b%3Dc", appLinkOf("deep_link_value" to "loyalty", "deep_link_sub1" to "a&b=c"))
    }

    @Test
    fun anyScreenNameOpensItsAppLink() {
        assertEquals("tonkeeper://swap", appLinkOf("deep_link_value" to "swap"))
        assertEquals("tonkeeper://main", appLinkOf("deep_link_value" to "main"))
        assertEquals("tonkeeper://swap", appLinkOf("deep_link_value" to " SWAP "))
        assertEquals("tonkeeper://buy-ton", appLinkOf("deep_link_value" to "buy-ton"))
    }

    @Test
    fun aLinkWithoutAScreenOpensMain() {
        assertEquals("tonkeeper://main", appLinkOf())
        assertEquals("tonkeeper://main", appLinkOf("deep_link_value" to " "))
        assertEquals("tonkeeper://main", appLinkOf("deep_link_value" to null))
    }

    @Test
    fun theReferralCodeGoesOnlyToLoyalty() {
        assertEquals("tonkeeper://swap", appLinkOf("deep_link_value" to "swap", "deep_link_sub1" to "UQJS7K21w"))
        assertEquals("tonkeeper://main", appLinkOf("deep_link_sub1" to "UQJS7K21w"))
    }

    @Test
    fun aScreenValueCannotCarryItsOwnPathOrQuery() {
        assertEquals("tonkeeper://swap%3Fft%3Dton%26tt%3Dusdt", appLinkOf("deep_link_value" to "swap?ft=ton&tt=usdt"))
        assertEquals("tonkeeper://transfer%2Fuqabc", appLinkOf("deep_link_value" to "transfer/UQAbc"))
        assertEquals("tonkeeper://tonkeeper%3A%2F%2Fswap", appLinkOf("deep_link_value" to "tonkeeper://swap"))
        assertEquals("tonkeeper://my%20screen", appLinkOf("deep_link_value" to "my screen"))
    }

    @Test
    fun theUtmOfTheLinkGoesIntoTheAppLink() {
        assertEquals(
            "tonkeeper://swap?utm_source=telegram&utm_medium=post&utm_campaign=november&utm_term=wallet&utm_content=banner",
            appLinkOf(
                "deep_link_value" to "swap",
                "pid" to "af_app_invites",
                "c" to "resistance_referral",
                "utm_source" to "telegram",
                "utm_medium" to "post",
                "utm_campaign" to "november",
                "utm_term" to "wallet",
                "utm_content" to "banner",
            ),
        )
    }

    @Test
    fun loyaltyKeepsTheCodeNextToTheUtm() {
        assertEquals(
            "tonkeeper://loyalty?referral_code=UQJS7K21w&utm_source=af_app_invites&utm_campaign=resistance_referral",
            appLinkOf(
                "deep_link_value" to "loyalty",
                "deep_link_sub1" to "UQJS7K21w",
                "pid" to "af_app_invites",
                "c" to "resistance_referral",
            ),
        )
    }

    @Test
    fun anyUtmOfTheLinkKeepsTheFallbackOut() {
        assertEquals(
            "tonkeeper://swap?utm_content=banner",
            appLinkOf("deep_link_value" to "swap", "pid" to "x_ads", "af_channel" to "feed", "c" to "launch", "utm_content" to "banner"),
        )
    }

    @Test
    fun aLinkWithoutUtmFallsBackToItsMediaSourceChannelAndCampaign() {
        assertEquals(
            "tonkeeper://swap?utm_source=x_ads&utm_medium=feed&utm_campaign=launch",
            appLinkOf("deep_link_value" to "swap", "pid" to "x_ads", "af_channel" to "feed", "c" to "launch", "utm_source" to " "),
        )
    }

    @Test
    fun theSdkPayloadFallsBackToItsMediaSourceAndCampaign() {
        assertEquals(
            "tonkeeper://main?utm_source=x_ads&utm_campaign=launch",
            appLinkOf("media_source" to "x_ads", "campaign" to "launch", "is_deferred" to true, "match_type" to "referrer"),
        )
    }

    @Test
    fun theSdkPayloadPrefersItsResolvedMediaSourceToTheLinkOne() {
        assertEquals(
            "tonkeeper://main?utm_source=x_ads&utm_campaign=launch",
            appLinkOf("media_source" to "x_ads", "pid" to "stale", "campaign" to "launch", "c" to "stale"),
        )
    }

    @Test
    fun utmValuesSurviveTheAppLinkUnchanged() {
        val utm = UtmParameters(
            source = "tele gram",
            medium = "a&b=c",
            campaign = "Чёрная пятница 50%",
            term = "1+1",
            content = "#top/?x",
        )
        val appLink = appLinkOf(
            "deep_link_value" to "swap",
            "utm_source" to utm.source,
            "utm_medium" to utm.medium,
            "utm_campaign" to utm.campaign,
            "utm_term" to utm.term,
            "utm_content" to utm.content,
        )

        assertEquals(utm, UtmParameters.fromLink(appLink))
    }

    @Test
    fun onlyABareShortLinkIsLeftToTheSdk() {
        assertTrue(isSdkResolvedOneLink(listOf("Dd63", "abc123"), null))
        assertTrue(isSdkResolvedOneLink(listOf("Dd63", "abc123"), " "))
        assertFalse(isSdkResolvedOneLink(listOf("Dd63", "abc123"), "loyalty"))
        assertFalse(isSdkResolvedOneLink(listOf("Dd63"), null))
        assertFalse(isSdkResolvedOneLink(emptyList(), null))
    }

    @Test
    fun theOneLinkSubdomainAndTheBrandedDomainAreOneLinks() {
        assertTrue(isOneLinkHost("keeper.onelink.me"))
        assertTrue(isOneLinkHost("go.tonkeeper.com"))
        assertTrue(isOneLinkHost("Go.Tonkeeper.com"))
    }

    @Test
    fun otherHostsAreNotOneLinks() {
        assertFalse(isOneLinkHost(null))
        assertFalse(isOneLinkHost("link.tonkeeper.com"))
        assertFalse(isOneLinkHost("app.tonkeeper.com"))
        assertFalse(isOneLinkHost("onelink.me"))
    }
}

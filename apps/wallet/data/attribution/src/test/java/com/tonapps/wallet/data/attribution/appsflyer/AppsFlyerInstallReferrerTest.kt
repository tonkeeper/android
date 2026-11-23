package com.tonapps.wallet.data.attribution.appsflyer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsFlyerInstallReferrerTest {

    private val referralReferrer =
        "deep_link_value=loyalty&af_tranid=AbCdEf123456&deep_link_sub1=QATEST5&pid=af_app_invites&c=resistance_referral"

    @Test
    fun aDeferredLinkTakesItsCampaignFromTheInstallReferrer() {
        assertEquals(
            "tonkeeper://loyalty?referral_code=QATEST5&utm_source=af_app_invites&utm_campaign=resistance_referral",
            appsFlyerDeferredAppLink(mapOf("deep_link_value" to "loyalty", "deep_link_sub1" to "QATEST5"), referralReferrer),
        )
    }

    @Test
    fun theScreenComesFromTheDeepLinkNotTheReferrer() {
        assertEquals(
            "tonkeeper://swap?utm_source=af_app_invites&utm_campaign=resistance_referral",
            appsFlyerDeferredAppLink(mapOf("deep_link_value" to "swap"), referralReferrer),
        )
    }

    @Test
    fun aDeepLinkWithItsOwnCampaignIgnoresTheReferrer() {
        assertEquals(
            "tonkeeper://swap?utm_source=x_ads&utm_campaign=launch",
            appsFlyerDeferredAppLink(
                mapOf("deep_link_value" to "swap", "pid" to "x_ads", "c" to "launch"),
                "af_tranid=AbCd&utm_source=telegram&utm_content=banner",
            ),
        )
    }

    @Test
    fun theReferrerNeverPicksTheScreenOrTheCode() {
        assertEquals(
            "tonkeeper://main?utm_source=af_app_invites&utm_campaign=resistance_referral",
            appsFlyerDeferredAppLink(emptyMap(), referralReferrer),
        )
    }

    @Test
    fun theUtmOfTheReferrerGoesIntoTheAppLink() {
        assertEquals(
            "tonkeeper://swap?utm_source=telegram&utm_campaign=black%20friday",
            appsFlyerDeferredAppLink(
                mapOf("deep_link_value" to "swap"),
                "af_tranid=AbCd&pid=x_ads&utm_source=telegram&utm_campaign=black%20friday",
            ),
        )
    }

    @Test
    fun aReferrerNotFromAnAppsFlyerClickIsIgnored() {
        assertTrue(appsFlyerReferrerValuesOf("utm_source=google-play&utm_medium=organic").isEmpty())
        assertEquals(
            "tonkeeper://swap",
            appsFlyerDeferredAppLink(mapOf("deep_link_value" to "swap"), "utm_source=google-play&utm_medium=organic"),
        )
    }

    @Test
    fun noReferrerLeavesTheDeepLinkAsItIs() {
        assertTrue(appsFlyerReferrerValuesOf(null).isEmpty())
        assertTrue(appsFlyerReferrerValuesOf("").isEmpty())
        assertEquals(
            "tonkeeper://loyalty?referral_code=QATEST5",
            appsFlyerDeferredAppLink(mapOf("deep_link_value" to "loyalty", "deep_link_sub1" to "QATEST5"), null),
        )
    }

    @Test
    fun referrerValuesAreDecodedAndBrokenPartsKeptAsIs() {
        assertEquals(
            mapOf("af_tranid" to "AbCd", "c" to "50% off", "pid" to "x%zz"),
            appsFlyerReferrerValuesOf("af_tranid=AbCd&c=50%25%20off&pid=x%zz&empty=&=orphan"),
        )
    }
}

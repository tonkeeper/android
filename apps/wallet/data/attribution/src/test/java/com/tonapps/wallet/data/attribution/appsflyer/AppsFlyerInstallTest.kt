package com.tonapps.wallet.data.attribution.appsflyer

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.InstallApp.InstallAppAttributionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppsFlyerInstallTest {

    @Test
    fun organicInstallCarriesNoCampaign() {
        val install = appsFlyerInstallOf(
            mapOf(
                "af_status" to "Organic",
                "af_message" to "organic install",
                "is_first_launch" to true,
                "install_time" to "2026-11-12 10:00:00.000",
                "media_source" to "organic",
                "campaign" to "stray",
                "utm_source" to "stray",
            ),
        )

        assertEquals(AppsFlyerInstall(status = InstallAppAttributionStatus.Organic), install)
    }

    @Test
    fun nonOrganicInstallTakesTheUtmOfItsOneLink() {
        val install = appsFlyerInstallOf(
            mapOf(
                "af_status" to "Non-organic",
                "media_source" to "af_app_invites",
                "af_channel" to "referral",
                "campaign" to "resistance_referral",
                "campaign_id" to "c-42",
                "utm_source" to "telegram",
                "utm_medium" to "post",
                "utm_campaign" to "november",
                "utm_term" to "wallet",
                "utm_content" to "banner",
                "is_first_launch" to false,
            ),
        )

        assertEquals(
            AppsFlyerInstall(
                status = InstallAppAttributionStatus.NonOrganic,
                campaignId = "c-42",
                campaignName = "resistance_referral",
                utm = UtmParameters(
                    source = "telegram",
                    medium = "post",
                    campaign = "november",
                    term = "wallet",
                    content = "banner",
                ),
            ),
            install,
        )
    }

    @Test
    fun anyUtmOfTheOneLinkKeepsTheFallbackOut() {
        val install = appsFlyerInstallOf(
            mapOf(
                "af_status" to "Non-organic",
                "media_source" to "af_app_invites",
                "af_channel" to "referral",
                "campaign" to "resistance_referral",
                "utm_content" to "banner",
            ),
        )

        assertEquals(UtmParameters(content = "banner"), install?.utm)
    }

    @Test
    fun nonOrganicInstallWithoutUtmFallsBackToTheMediaSourceChannelAndCampaign() {
        val install = appsFlyerInstallOf(
            mapOf(
                "af_status" to "Non-organic",
                "media_source" to "af_app_invites",
                "af_channel" to "referral",
                "campaign" to "resistance_referral",
                "campaign_id" to "c-42",
                "utm_source" to " ",
            ),
        )

        assertEquals(
            AppsFlyerInstall(
                status = InstallAppAttributionStatus.NonOrganic,
                campaignId = "c-42",
                campaignName = "resistance_referral",
                utm = UtmParameters(source = "af_app_invites", medium = "referral", campaign = "resistance_referral"),
            ),
            install,
        )
    }

    @Test
    fun nonOrganicInstallWithoutCampaignDataIsStillNonOrganic() {
        val install = appsFlyerInstallOf(mapOf("af_status" to "Non-organic", "campaign" to null, "campaign_id" to ""))

        assertEquals(AppsFlyerInstall(status = InstallAppAttributionStatus.NonOrganic), install)
    }

    @Test
    fun unknownStatusIsNoInstall() {
        assertNull(appsFlyerInstallOf(mapOf("af_status" to "Unknown", "campaign" to "november")))
        assertNull(appsFlyerInstallOf(mapOf("af_status" to "non_organic")))
        assertNull(appsFlyerInstallOf(mapOf("af_status" to null)))
        assertNull(appsFlyerInstallOf(mapOf("campaign" to "november")))
        assertNull(appsFlyerInstallOf(emptyMap()))
    }
}

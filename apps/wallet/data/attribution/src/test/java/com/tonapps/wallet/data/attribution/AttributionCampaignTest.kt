package com.tonapps.wallet.data.attribution

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.wallet.data.attribution.entities.AttributionEvent
import com.tonapps.wallet.data.attribution.entities.AttributionProvider
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttributionCampaignTest {

    @Test
    fun branchTakesTheUtmTheClickCarried() {
        val utm = branchUtm(
            mapOf(
                "utm_source" to "br_src",
                "utm_medium" to "br_med",
                "utm_campaign" to "br_camp",
                "~channel" to "telegram",
                "~campaign" to "launch",
            )
        )

        assertEquals(UtmParameters(source = "br_src", medium = "br_med", campaign = "br_camp"), utm)
    }

    @Test
    fun branchFallsBackToItsOwnTagsWhenTheLinkHasNoUtm() {
        val utm = branchUtm(
            mapOf(
                "~channel" to "telegram",
                "~feature" to "post",
                "~campaign" to "multichain_launch",
            )
        )

        assertEquals(UtmParameters(source = "telegram", medium = "post", campaign = "multichain_launch"), utm)
    }

    @Test
    fun branchBlankTagsAreNoCampaign() {
        val utm = branchUtm(mapOf("~channel" to "", "~feature" to " ", "~campaign" to "", "utm_source" to ""))

        assertEquals(UtmParameters.Empty, utm)
    }

    @Test
    fun branchInstallNamesItsLinkAndCampaign() {
        val touch = touch(
            provider = AttributionProvider.Branch,
            link = "https://go.tonkeeper.com/AczE4ghgC6b",
            params = mapOf("~id" to "1630585442128807562", "~campaign" to "multichain_launch"),
        )

        assertEquals("1630585442128807562", touch.campaignId)
        assertEquals("multichain_launch", touch.campaignName)
    }

    @Test
    fun linkrunnerInstallTakesTheUtmOfItsCampaignLink() {
        val touch = touch(
            provider = AttributionProvider.Linkrunner,
            link = "https://link.tonkeeper.com/swap?c=DCjGwC3opWs&utm_source=lr_src&utm_campaign=lr_camp",
            params = mapOf("campaign_id" to "DCjGwC3opWs", "campaign_name" to "Launch", "campaign_type" to "PAID"),
        )

        assertEquals(UtmParameters(source = "lr_src", campaign = "lr_camp"), touch.campaignUtm)
        assertEquals("DCjGwC3opWs", touch.campaignId)
        assertEquals("Launch", touch.campaignName)
    }

    @Test
    fun linkrunnerInstallWithoutUtmIsNamedByItsCampaignOnly() {
        val touch = touch(
            provider = AttributionProvider.Linkrunner,
            link = "https://link.tonkeeper.com/swap?c=DCjGwC3opWs",
            params = mapOf("campaign_id" to "DCjGwC3opWs", "campaign_name" to "Launch"),
        )

        assertEquals(UtmParameters.Empty, touch.campaignUtm)
        assertEquals("DCjGwC3opWs", touch.campaignId)
        assertEquals("Launch", touch.campaignName)
    }

    @Test
    fun branchInstallWithoutCampaignFieldsHasNoCampaignName() {
        val touch = touch(
            provider = AttributionProvider.Branch,
            link = "https://go.tonkeeper.com/AczE4ghgC6b",
            params = mapOf("~id" to "1630585442128807562", "~campaign" to ""),
        )

        assertNull(touch.campaignName)
    }

    @Test
    fun campaignUtmIsAppendedToALinkWithoutQuery() {
        val link = "https://app.tonkeeper.com/swap".withCampaignUtm(UtmParameters(source = "br_src", campaign = "br_camp"))

        assertEquals("https://app.tonkeeper.com/swap?utm_source=br_src&utm_campaign=br_camp", link)
    }

    @Test
    fun campaignUtmKeepsTheLinkQueryAndFragment() {
        val link = "tonkeeper://swap?ft=TON#top".withCampaignUtm(UtmParameters(source = "br_src"))

        assertEquals("tonkeeper://swap?ft=TON&utm_source=br_src#top", link)
    }

    @Test
    fun aLinkWithItsOwnUtmIsLeftAsIs() {
        val link = "https://app.tonkeeper.com/swap?utm_source=own"

        assertEquals(link, link.withCampaignUtm(UtmParameters(source = "br_src", campaign = "br_camp")))
    }

    @Test
    fun appendedUtmReadsBackAsTheSameValues() {
        val utm = UtmParameters(source = "tg ads", medium = "c+p", campaign = "summer/2026", content = "кнопка")

        val link = "https://app.tonkeeper.com/swap".withCampaignUtm(utm)

        assertEquals(utm, UtmParameters.fromLink(link))
    }

    @Test
    fun campaignUtmGoesOnTheBareAppLink() {
        val utm = UtmParameters(source = "br_src", campaign = "br_camp")

        val link = "tonkeeper://".withCampaignUtm(utm)

        assertEquals("tonkeeper://?utm_source=br_src&utm_campaign=br_camp", link)
        assertEquals(utm, UtmParameters.fromLink(link))
    }

    @Test
    fun noCampaignLeavesTheLinkAsIs() {
        assertEquals("https://app.tonkeeper.com/swap", "https://app.tonkeeper.com/swap".withCampaignUtm(UtmParameters.Empty))
    }

    private fun touch(
        provider: AttributionProvider,
        link: String?,
        params: Map<String, String>,
    ) = AttributionTouch(
        provider = provider,
        event = AttributionEvent.Install,
        attributedAt = 0L,
        link = link,
        params = params,
    )
}

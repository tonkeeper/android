package com.tonapps.bus.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtmParametersTest {

    @Test
    fun everyStandardParameterIsRead() {
        val utm = UtmParameters.fromLink(
            "tonkeeper://staking?utm_source=newsletter&utm_medium=email" +
                "&utm_campaign=autumn_2026&utm_term=stake&utm_content=header_button"
        )

        assertEquals(
            UtmParameters(
                source = "newsletter",
                medium = "email",
                campaign = "autumn_2026",
                term = "stake",
                content = "header_button",
            ),
            utm,
        )
        assertFalse(utm.isEmpty)
    }

    @Test
    fun universalLinksAndPartialTaggingAreRead() {
        val utm = UtmParameters.fromLink("https://app.tonkeeper.com/transfer/EQD?amount=1&utm_source=promo")

        assertEquals("promo", utm.source)
        assertNull(utm.campaign)
    }

    @Test
    fun opaqueLinksAreRead() {
        val utm = UtmParameters.fromLink("ethereum:0xde0B295669a9FD93d5F28D9Ec85E40f4cb697BAe@1?value=1&utm_campaign=evm")

        assertEquals("evm", utm.campaign)
    }

    @Test
    fun doubleEncodedLinkStillCarriesItsCampaign() {
        val utm = UtmParameters.fromLink("tonkeeper://dapp/https%253A%252F%252Fapp.io%253Futm_source%253Dpromo")

        assertEquals("promo", utm.source)
    }

    @Test
    fun untaggedLinksCarryNothing() {
        assertTrue(UtmParameters.fromLink(null).isEmpty)
        assertTrue(UtmParameters.fromLink("").isEmpty)
        assertTrue(UtmParameters.fromLink("tonkeeper://staking").isEmpty)
        assertTrue(UtmParameters.fromLink("tonkeeper://staking?utm_source=").isEmpty)
        assertTrue(UtmParameters.fromLink("tonkeeper://staking?utm_source=%20%20").isEmpty)
        assertTrue(UtmParameters.fromLink("tonkeeper://staking?UTM_SOURCE=promo").isEmpty)
        assertTrue(
            UtmParameters.fromLink(
                "tonkeeper://browser?url=https%3A%2F%2Fexample.com%2F%3Fx%3D1%26utm_source%3Dnested"
            ).isEmpty
        )
    }

    @Test
    fun valuesAreDecodedAndTrimmed() {
        val utm = UtmParameters.fromLink("tonkeeper://staking?utm_source=%20promo%20&utm_term=ton+wallet&utm_content=a%26b#top")

        assertEquals("promo", utm.source)
        assertEquals("ton+wallet", utm.term)
        assertEquals("a&b", utm.content)
    }

    @Test
    fun theFirstOccurrenceWins() {
        val utm = UtmParameters.fromLink("tonkeeper://staking?utm_source=first&utm_source=second")

        assertEquals("first", utm.source)
    }

    @Test
    fun eventPropsCarryOnlyTheTagsTheLinkHad() {
        val props = UtmParameters(source = "push", campaign = "autumn").toEventProps()

        assertEquals(mapOf("utm_source" to "push", "utm_campaign" to "autumn"), props)
    }
}

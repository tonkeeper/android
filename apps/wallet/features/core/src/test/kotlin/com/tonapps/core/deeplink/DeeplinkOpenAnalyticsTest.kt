package com.tonapps.core.deeplink

import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenFrom
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenLinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeeplinkOpenAnalyticsTest {

    @Test
    fun httpsLinksAreUniversalAndEveryOtherSchemeIsAnAppLink() {
        assertEquals(DeeplinkOpenFrom.UniversalLink, DeeplinkOpenAnalytics.from("https://app.tonkeeper.com/staking"))
        assertEquals(DeeplinkOpenFrom.UniversalLink, DeeplinkOpenAnalytics.from("HTTP://app.tonkeeper.com/staking"))
        assertEquals(DeeplinkOpenFrom.UniversalLink, DeeplinkOpenAnalytics.from("https://app.keeperwallet.com/staking"))
        assertEquals(DeeplinkOpenFrom.AppLink, DeeplinkOpenAnalytics.from("tonkeeper://staking"))
        assertEquals(DeeplinkOpenFrom.AppLink, DeeplinkOpenAnalytics.from("ton://transfer/EQD"))
        assertEquals(DeeplinkOpenFrom.AppLink, DeeplinkOpenAnalytics.from("tc://?v=2"))
        assertEquals(DeeplinkOpenFrom.AppLink, DeeplinkOpenAnalytics.from("wc:topic@2?symKey=key"))
    }

    @Test
    fun onlyAWalletConnectUriWithASymKeyIsAPairing() {
        assertTrue(DeeplinkOpenAnalytics.isWalletConnectPairing("wc:topic@2?relay-protocol=irn&symKey=key&expiryTimestamp=1"))
        assertTrue(DeeplinkOpenAnalytics.isWalletConnectPairing("wc:topic@2?symkey=key"))
        assertFalse(DeeplinkOpenAnalytics.isWalletConnectPairing("wc:topic@2?requestId=1&sessionTopic=session"))
        assertFalse(DeeplinkOpenAnalytics.isWalletConnectPairing("wc:topic@2?symKey="))
        assertFalse(DeeplinkOpenAnalytics.isWalletConnectPairing("wc:topic@2"))
    }

    @Test
    fun linkTypeNamesWhatTheLinkOpens() {
        assertEquals(DeeplinkOpenLinkType.Staking, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Staking))
        assertEquals(DeeplinkOpenLinkType.Swap, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Swap(from = "TON", to = null)))
        assertEquals(DeeplinkOpenLinkType.Transfer, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Send(address = null)))
        assertEquals(DeeplinkOpenLinkType.Deposit, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Purchase))
        assertEquals(DeeplinkOpenLinkType.Receive, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Receive))
        assertEquals(DeeplinkOpenLinkType.Backup, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Backups))
        assertEquals(DeeplinkOpenLinkType.Main, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Tabs.Main(from = "deep-link")))
        assertEquals(DeeplinkOpenLinkType.Raffle, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Raffle(id = null)))
    }

    @Test
    fun linksWithoutASchemaValueAreUnknown() {
        assertEquals(DeeplinkOpenLinkType.Unknown, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Settings))
        assertEquals(DeeplinkOpenLinkType.Unknown, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Tabs.Activity(from = "deep-link")))
        assertEquals(DeeplinkOpenLinkType.Unknown, DeeplinkOpenAnalytics.linkType(DeepLinkRoute.Jetton(address = "TON")))
    }
}

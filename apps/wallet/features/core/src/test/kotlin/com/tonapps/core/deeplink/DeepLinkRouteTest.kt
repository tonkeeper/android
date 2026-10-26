package com.tonapps.core.deeplink

import org.junit.Assert.assertFalse
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
}

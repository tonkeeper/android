package com.tonapps.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkReferralCodeTest {

    private val loyalty = DeepLinkRoute.Loyalty("K7M2QX9P")

    @Test
    fun outsideOrScannedLinkCarriesItsReferralCode() {
        assertEquals("K7M2QX9P", DeepLink(loyalty, fromQR = false, referrer = null, fromExternal = true).referralCodeFromOutsideLink)
        assertEquals("K7M2QX9P", DeepLink(loyalty, fromQR = true, referrer = null).referralCodeFromOutsideLink)
    }

    @Test
    fun inAppLinkCarriesNoReferralCode() {
        assertNull(DeepLink(loyalty, fromQR = false, referrer = null).referralCodeFromOutsideLink)
    }

    @Test
    fun loyaltyLinkWithoutACodeOrAnotherRouteCarriesNoReferralCode() {
        assertNull(DeepLink(DeepLinkRoute.Loyalty(null), fromQR = true, referrer = null, fromExternal = true).referralCodeFromOutsideLink)
        assertNull(DeepLink(DeepLinkRoute.Staking, fromQR = true, referrer = null, fromExternal = true).referralCodeFromOutsideLink)
    }
}

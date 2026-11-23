package com.tonapps.core.loyalty

import org.junit.Assert.assertEquals
import org.junit.Test

class LoyaltyUrlTest {

    @Test
    fun withoutAReferralCodeTheHomePageOpens() {
        assertEquals("https://loyalty.keeperwallet.com/", loyaltyUrlOf(null))
    }

    @Test
    fun referralCodeOpensTheReferralsPageWithTheCode() {
        assertEquals(
            "https://loyalty.keeperwallet.com/referrals?referralCode=UQJS7K21w",
            loyaltyUrlOf("UQJS7K21w"),
        )
    }

    @Test
    fun referralCodeIsEncodedIntoTheQuery() {
        assertEquals(
            "https://loyalty.keeperwallet.com/referrals?referralCode=a%26b%3Dc",
            loyaltyUrlOf("a&b=c"),
        )
    }
}

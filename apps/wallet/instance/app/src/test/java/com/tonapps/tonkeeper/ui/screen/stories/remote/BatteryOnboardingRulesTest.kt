package com.tonapps.tonkeeper.ui.screen.stories.remote

import com.tonapps.icu.Coins
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BatteryOnboardingRulesTest {

    @Test
    fun offersForZeroBalance() {
        assertTrue(isBatteryOnboardingOffered(Coins.ZERO, storyViewed = false))
    }

    @Test
    fun offersForZeroBalanceWithScale() {
        assertTrue(isBatteryOnboardingOffered(Coins(BigDecimal("0.000"), 9), storyViewed = false))
    }

    @Test
    fun skipsPositiveBalance() {
        assertFalse(isBatteryOnboardingOffered(Coins.of("0.5"), storyViewed = false))
    }

    @Test
    fun skipsNegativeBalance() {
        assertFalse(isBatteryOnboardingOffered(Coins(BigDecimal("-0.5"), 9), storyViewed = false))
    }

    @Test
    fun skipsUnknownBalance() {
        assertFalse(isBatteryOnboardingOffered(null, storyViewed = false))
    }

    @Test
    fun skipsViewedStory() {
        assertFalse(isBatteryOnboardingOffered(Coins.ZERO, storyViewed = true))
    }
}

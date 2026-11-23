package com.tonapps.tonkeeper.ui.screen.stories.remote

import com.tonapps.icu.Coins

const val BATTERY_ONBOARDING_STORY_ID = "battery_onboarding"
const val BATTERY_ONBOARDING_FROM = "wallet"
const val BATTERY_ONBOARDING_TIMEOUT_MS = 2_000L

fun isBatteryOnboardingOffered(balance: Coins?, storyViewed: Boolean): Boolean =
    balance != null && !balance.isPositive && !balance.isNegative && !storyViewed

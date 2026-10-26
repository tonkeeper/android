package com.tonapps.core.components

import androidx.compose.runtime.Immutable
import com.tonapps.bus.generated.Events.Banner.BannerFrom
import com.tonapps.bus.generated.Events.Banner.BannerWalletMode

@Immutable
data class BannerAnalytics(
    val walletMode: BannerWalletMode,
    val from: BannerFrom,
)

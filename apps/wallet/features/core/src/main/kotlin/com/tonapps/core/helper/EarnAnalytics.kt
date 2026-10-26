package com.tonapps.core.helper

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.EarnFlow.EarnEntryViewFrom

object EarnAnalytics {

    private val events: Events.EarnFlow
        get() = AnalyticsHelper.Default.events.earnFlow

    fun featuredView(vaultsCount: Int) {
        events.earnEntryView(
            from = EarnEntryViewFrom.EarnFeatured,
            vaultsCount = vaultsCount,
            balanceUsd = null,
            asset = null,
        )
    }

    fun yourVaultsView(positionsCount: Int, balanceUsd: Double) {
        events.earnEntryView(
            from = EarnEntryViewFrom.EarnYourVaults,
            vaultsCount = positionsCount,
            balanceUsd = balanceUsd,
            asset = null,
        )
    }

    fun apyView(assetId: String) {
        events.earnEntryView(
            from = EarnEntryViewFrom.EarnApy,
            vaultsCount = null,
            balanceUsd = null,
            asset = assetId,
        )
    }
}
package com.tonapps.wallet.features.events.data

data class WalletActivitiesPage(
    val activities: List<HistoryEventEntity>,
    val cursor: String?,
)

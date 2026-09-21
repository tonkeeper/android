package com.tonapps.wallet.features.events.screens

import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityType

internal enum class EventsTypeFilter(
    val apiType: ActivityType?,
    val apiIsSpam: Boolean,
    val titleRes: Int,
) {
    All(null, false, Localization.all_types),
    Sent(ActivityType.send, false, Localization.sent),
    Received(ActivityType.receive, false, Localization.received),
    Swap(ActivityType.swap, false, Localization.swap),
    Spam(null, true, Localization.spam),
}

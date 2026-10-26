package com.tonapps.wallet.features.events.screens

import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityTypeFilter

internal enum class EventsTypeFilter(
    val apiType: ActivityTypeFilter?,
    val apiIsSpam: Boolean,
    val titleRes: Int,
    val requestsPerps: Boolean = false,
) {
    All(null, false, Localization.all_types, requestsPerps = true),
    Sent(ActivityTypeFilter.send, false, Localization.sent),
    Received(ActivityTypeFilter.receive, false, Localization.received),
    Swap(ActivityTypeFilter.swap, false, Localization.swap_event),
    Perps(ActivityTypeFilter.perps, false, Localization.perps_title, requestsPerps = true),
    Spam(null, true, Localization.spam),
    ;

    companion object {
        fun visible(perpsAllowed: Boolean): List<EventsTypeFilter> =
            entries.filter { it != Perps || perpsAllowed }
    }
}

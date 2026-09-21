package com.tonapps.wallet.features.events.screens

import androidx.compose.runtime.Immutable
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import ui.components.moon.cell.MoonBundlePosition

@Immutable
sealed class WalletActivityListItem {

    abstract val listKey: String

    @Immutable
    data class DateHeader(
        val sectionKey: String,
        val title: String,
    ) : WalletActivityListItem() {
        override val listKey: String get() = "header_$sectionKey"
    }

    @Immutable
    data class Entry(
        val activity: HistoryEventEntity,
    ) : WalletActivityListItem() {
        override val listKey: String get() = activity.txIds.joinToString(",").ifEmpty { "no_tx" }
    }
}

/**
 * [MoonBundlePosition] for an activity row between [WalletActivityListItem.DateHeader]s.
 * [appendIsLoading] / [appendEndReached] describe [androidx.paging.compose.LazyPagingItems.loadState.append].
 */
internal fun moonBundlePositionForWalletActivityEntry(
    previous: WalletActivityListItem?,
    next: WalletActivityListItem?,
    appendIsLoading: Boolean,
    appendEndReached: Boolean,
): MoonBundlePosition {
    val firstInSection = previous == null || previous is WalletActivityListItem.DateHeader
    val lastInSection = when (next) {
        is WalletActivityListItem.DateHeader -> true
        is WalletActivityListItem.Entry -> false
        null -> when {
            appendIsLoading -> false
            appendEndReached -> true
            else -> false
        }
    }
    return when {
        firstInSection && lastInSection -> MoonBundlePosition.Default
        firstInSection -> MoonBundlePosition.Header
        lastInSection -> MoonBundlePosition.Footer
        else -> MoonBundlePosition.Middle
    }
}

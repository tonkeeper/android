package com.tonapps.tonkeeper.ui.screen.events

import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

data class EventsUiState(
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true,
) {

    val isEmpty: Boolean
        get() = items.isEmpty() && !isLoading
}
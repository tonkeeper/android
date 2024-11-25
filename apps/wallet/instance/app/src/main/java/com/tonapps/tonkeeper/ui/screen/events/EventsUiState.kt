package com.tonapps.tonkeeper.ui.screen.events

import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

data class EventsUiState(
    val uiItems: List<HistoryItem>,
    val loading: Boolean
) {

    val isFooterLoading: Boolean = uiItems.lastOrNull()?.let { it is HistoryItem.Loader } ?: false
}
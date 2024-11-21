package com.tonapps.tonkeeper.ui.screen.events

import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

data class EventsUiState(
    val uiItems: List<HistoryItem>,
    val loading: Boolean
)
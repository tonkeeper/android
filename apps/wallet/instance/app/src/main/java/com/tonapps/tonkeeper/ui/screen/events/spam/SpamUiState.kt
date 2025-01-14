package com.tonapps.tonkeeper.ui.screen.events.spam

import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

data class SpamUiState(
    val uiItems: List<HistoryItem> = listOf(HistoryItem.Loader(0, 0L)),
    val loading: Boolean = true
) {
}
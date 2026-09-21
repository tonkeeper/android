package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

import com.tonapps.wallet.features.events.components.legacy.UiEvent

data class TxNewestItemsState(
    val loading: Boolean = false,
    val items: List<UiEvent.Item> = emptyList(),
)
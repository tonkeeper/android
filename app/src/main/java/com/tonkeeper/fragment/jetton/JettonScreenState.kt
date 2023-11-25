package com.tonkeeper.fragment.jetton

import com.tonkeeper.core.history.list.item.HistoryItem
import io.tonapi.models.JettonBalance
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class JettonScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val jetton: JettonBalance? = null,
    val currencyBalance: String = "",
    val rateFormat: String = "",
    val rate24h: String = "",
    val historyItems: List<HistoryItem> = emptyList()
): UiState()
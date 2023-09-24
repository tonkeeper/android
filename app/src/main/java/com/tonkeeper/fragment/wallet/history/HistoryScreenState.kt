package com.tonkeeper.fragment.wallet.history

import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiState

data class HistoryScreenState(
    val asyncState: AsyncState = AsyncState.Default,
): UiState()
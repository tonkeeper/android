package com.tonkeeper.fragment.wallet.history

import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiFeature

class HistoryScreenFeature: UiFeature<HistoryScreenState>(HistoryScreenState()) {

    init {
        loadEvents()
    }

    private fun loadEvents() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }
    }
}
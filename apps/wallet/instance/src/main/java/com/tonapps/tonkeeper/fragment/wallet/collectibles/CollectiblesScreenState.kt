package com.tonapps.tonkeeper.fragment.wallet.collectibles

import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.CollectiblesItem
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class CollectiblesScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val items: List<CollectiblesItem> = emptyList()
): UiState()
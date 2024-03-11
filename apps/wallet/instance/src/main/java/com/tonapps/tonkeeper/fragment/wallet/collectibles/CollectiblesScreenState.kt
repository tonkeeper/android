package com.tonapps.tonkeeper.fragment.wallet.collectibles

import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.Item
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class CollectiblesScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val items: List<Item> = Item.Skeleton.list
): UiState()
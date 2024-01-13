package com.tonkeeper.fragment.wallet.main

import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class WalletScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val title: String? = null,
    val items: List<WalletItem> = emptyList()
): UiState()
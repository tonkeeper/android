package com.tonkeeper.fragment.wallet.main

import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import ton.wallet.WalletType
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class WalletScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val title: String? = null,
    val walletType: WalletType = WalletType.Default,
    val items: List<WalletItem> = emptyList()
): UiState() {

    val actionEnabled: Boolean
        get() = walletType != WalletType.Watch
}
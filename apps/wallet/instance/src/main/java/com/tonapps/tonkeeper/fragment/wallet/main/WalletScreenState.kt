package com.tonapps.tonkeeper.fragment.wallet.main

import android.graphics.Color
import com.tonapps.tonkeeper.data.AccountColor
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonapps.wallet.data.account.entities.WalletLabel
import ton.wallet.WalletType
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class WalletScreenState(
    val asyncState: AsyncState = AsyncState.Loading,
    val walletLabel: WalletLabel = WalletLabel("", "", AccountColor.all.first()),
    val walletType: WalletType = WalletType.Default,
    val items: List<WalletItem> = emptyList()
): UiState() {

    val actionEnabled: Boolean
        get() = walletType != WalletType.Watch
}
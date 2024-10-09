package com.tonapps.tonkeeper.ui.screen.send.transaction

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.account.entities.WalletEntity

sealed class SendTransactionState {
    data object Loading: SendTransactionState()
    data object Failed: SendTransactionState()
    data object FailedEmulation: SendTransactionState()

    data class InsufficientBalance(
        val wallet: WalletEntity,
        val balance: Coins,
        val required: Coins,
        val withRechargeBattery: Boolean,
        val singleWallet: Boolean
    ): SendTransactionState()

    data class Details(
        val emulated: HistoryHelper.Details,
        val totalFormat: CharSequence,
        val isDangerous: Boolean,
        val nftCount: Int
    ): SendTransactionState() {

        val uiItems: List<HistoryItem>
            get() = emulated.items
    }
}
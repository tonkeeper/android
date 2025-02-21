package com.tonapps.tonkeeper.ui.screen.send.transaction

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.wallet.data.account.entities.WalletEntity

sealed class SendTransactionState {
    data object Loading: SendTransactionState()
    data object Failed: SendTransactionState()
    data object FailedEmulation: SendTransactionState()

    data class InsufficientBalance(
        val wallet: WalletEntity,
        val balance: Amount,
        val required: Amount,
        val withRechargeBattery: Boolean,
        val singleWallet: Boolean,
        val type: InsufficientBalanceType
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
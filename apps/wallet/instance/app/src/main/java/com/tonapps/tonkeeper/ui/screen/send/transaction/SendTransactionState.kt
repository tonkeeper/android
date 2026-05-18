package com.tonapps.tonkeeper.ui.screen.send.transaction

import com.tonapps.blockchain.model.legacy.Amount
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.blockchain.model.legacy.WalletEntity

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
        val nftCount: Int,
        val failed: Boolean,
        val fee: SendFee,
    ): SendTransactionState() {

        val uiItems: List<HistoryItem>
            get() = emulated.items
    }
}
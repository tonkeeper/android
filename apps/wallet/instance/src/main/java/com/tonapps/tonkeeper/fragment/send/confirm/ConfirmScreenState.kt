package com.tonapps.tonkeeper.fragment.send.confirm

import com.tonapps.blockchain.Coins
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.account.entities.WalletLabel
import uikit.mvi.UiState
import uikit.widget.ProcessTaskView

data class ConfirmScreenState(
    val amount: CharSequence? = null,
    val amountInCurrency: String? = null,
    val feeValue: Coins = Coins.ZERO,
    val fee: String? = null,
    val feeInCurrency: String? = null,
    val processActive: Boolean = false,
    val processState: ProcessTaskView.State = ProcessTaskView.State.LOADING,
    val buttonEnabled: Boolean = false,
    val emulatedEventItems: List<HistoryItem> = emptyList(),
    val signer: Boolean = false,
    val walletLabel: WalletLabel? = null
): UiState()
package com.tonkeeper.fragment.send.confirm

import com.tonkeeper.core.history.list.item.HistoryItem
import uikit.mvi.UiState
import uikit.widget.ProcessTaskView

data class ConfirmScreenState(
    val amount: String? = null,
    val amountInCurrency: String? = null,
    val feeValue: Long = 0,
    val fee: String? = null,
    val feeInCurrency: String? = null,
    val processActive: Boolean = false,
    val processState: ProcessTaskView.State = ProcessTaskView.State.LOADING,
    val buttonEnabled: Boolean = false,
    val emulatedEventItems: List<HistoryItem> = emptyList(),
    val signer: Boolean = false
): UiState()
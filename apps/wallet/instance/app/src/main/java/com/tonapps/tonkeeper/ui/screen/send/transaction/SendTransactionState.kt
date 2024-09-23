package com.tonapps.tonkeeper.ui.screen.send.transaction

import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

sealed class SendTransactionState {
    data object Loading: SendTransactionState()
    data object Failed: SendTransactionState()

    data class Details(
        val emulated: HistoryHelper.Details,
        val totalFormat: CharSequence,
        val isDangerous: Boolean,
        val nftCount: Int,
    ): SendTransactionState() {

        val uiItems: List<HistoryItem>
            get() = emulated.items
    }
}
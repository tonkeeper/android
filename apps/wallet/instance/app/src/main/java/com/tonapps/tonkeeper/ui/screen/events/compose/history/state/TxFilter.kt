package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

import android.content.Context
import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.tx.model.TxAction
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.localization.Localization
import ui.components.filter.UiFilter

enum class TxFilter(
    val id: Int,
    val titleResId: Int
) {
    All(1, Localization.all),
    Send(2, Localization.send),
    Received(3, Localization.received),
    Spam(4, Localization.spam),
}

internal val supportedTxFilters = arrayOf(TxFilter.All, TxFilter.Send, TxFilter.Received, TxFilter.Spam)

fun TxFilter.toUi(context: Context) = UiFilter(
    id = id,
    title = context.getString(titleResId)
)

internal fun TxAction.getFilters(): List<TxFilter> {
    val filters = mutableListOf<TxFilter>()
    if (type == ActionType.Send || type == ActionType.NftSend || type == ActionType.DepositStake || type == ActionType.Swap) {
        filters.add(TxFilter.Send)
    }

    if (type == ActionType.Received || type == ActionType.NftReceived || type == ActionType.WithdrawStake || type == ActionType.Refund || type == ActionType.Swap) {
        filters.add(TxFilter.Received)
    }

    return filters.toList()
}

internal fun TxEvent.getFilters(): List<TxFilter> {
    return actions.map { it.getFilters() }.flatten().distinctBy { it.id }
}

fun TxFilter.isMatch(event: TxEvent): Boolean {
    return if (this == TxFilter.All) {
        !event.spam
    } else if (this == TxFilter.Send) {
        event.containsActionType(ActionType.Send, ActionType.NftSend, ActionType.DepositStake, ActionType.Swap)
    } else if (this == TxFilter.Received) {
        event.containsActionType(ActionType.Received, ActionType.NftReceived, ActionType.WithdrawStake, ActionType.Refund, ActionType.Swap)
    } else if (this == TxFilter.Spam) {
        event.spam
    } else {
        false
    }
}
package com.tonkeeper.fragment.wallet.history.list

import uikit.list.BaseListItem

data class HistoryItem(
    val action: Action,
    val subtitle: String,
    val timestamp: Long,
): BaseListItem() {

    enum class Action {
        Received, Send
    }

}
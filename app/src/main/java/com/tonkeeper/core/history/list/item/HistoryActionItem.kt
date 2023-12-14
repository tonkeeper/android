package com.tonkeeper.core.history.list.item

import uikit.list.ListCell

data class HistoryActionItem(
    val iconURL: String? = null,
    val action: Action,
    val title: String,
    val subtitle: String,
    val timestamp: Long = 0L,
    val comment: String? = null,
    val value: String,
    val value2: String = "",
    val nftImageURL: String? = null,
    val nftTitle: String? = null,
    val nftCollection: String? = null,
    val tokenCode: String? = null,
    val date: String = "",
    val pending: Boolean = false,
    val position: ListCell.Position = ListCell.Position.SINGLE
): HistoryItem(TYPE_ACTION) {

    enum class Action {
        Received, Send, CallContract, NftReceived, NftSend, Swap
    }

    val hasNft: Boolean
        get() = nftImageURL != null && nftTitle != null && nftCollection != null
}
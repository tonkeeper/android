package com.tonkeeper.fragment.wallet.history.list.item

data class HistoryActionItem(
    val iconURL: String? = null,
    val action: Action,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val comment: String? = null,
    val value: String,
    val nftImageURL: String? = null,
    val nftTitle: String? = null,
    val nftCollection: String? = null
): HistoryItem(TYPE_ACTION) {

    enum class Action {
        Received, Send, CallContract, NftReceived, NftSend,
    }

    val hasNft: Boolean
        get() = nftImageURL != null && nftTitle != null && nftCollection != null
}
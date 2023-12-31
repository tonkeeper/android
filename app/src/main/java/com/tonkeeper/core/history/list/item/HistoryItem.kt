package com.tonkeeper.core.history.list.item

import android.content.Context
import com.tonkeeper.core.history.ActionType
import com.tonkeeper.core.history.TransactionDetails
import com.tonkeeper.helper.DateFormat
import uikit.list.BaseListItem
import uikit.list.ListCell

sealed class HistoryItem(
    type: Int,
): BaseListItem(type) {

    companion object {
        const val TYPE_ACTION = 1
        const val TYPE_HEADER = 2
        const val TYPE_SPACE = 3
    }

    data object Space: HistoryItem(TYPE_SPACE)

    data class Header(
        val title: String,
        val titleResId: Int? = null
    ): HistoryItem(TYPE_HEADER) {

        constructor(timestamp: Long) : this(
            DateFormat.monthWithDate(timestamp)
        )

        constructor(titleResId: Int) : this(
            title = "",
            titleResId = titleResId
        )
    }

    data class Event(
        val txId: String,
        val iconURL: String? = null,
        val action: ActionType,
        val title: String,
        val subtitle: String,
        val timestamp: Long = 0L,
        val comment: String? = null,
        val value: String,
        val value2: String = "",
        val currency: String? = null,
        val nftImageURL: String? = null,
        val nftTitle: String? = null,
        val nftCollection: String? = null,
        val nftAddress: String? = null,
        val tokenCode: String? = null,
        val date: String = "",
        val pending: Boolean = false,
        val position: ListCell.Position = ListCell.Position.SINGLE,
        val coinIconUrl: String = "",
        val fee: String? = null,
        val feeInCurrency: String? = null,
        val isOut: Boolean,
        val address: String? = null,
        val addressName: String? = null,
    ): HistoryItem(TYPE_ACTION) {

        val hasNft: Boolean
            get() = nftImageURL != null && nftTitle != null && nftCollection != null
    }
}
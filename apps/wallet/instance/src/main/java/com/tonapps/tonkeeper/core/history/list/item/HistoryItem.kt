package com.tonapps.tonkeeper.core.history.list.item

import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.helper.DateFormat

sealed class HistoryItem(
    type: Int,
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val TYPE_ACTION = 1
        const val TYPE_HEADER = 2
        const val TYPE_SPACE = 3
        const val TYPE_LOADER = 4

        private fun getId(item: HistoryItem): Long {
            val hashCode = if (item is Event) {
                item.txId.hashCode()
            } else {
                item.toString().hashCode()
            }
            return hashCode.toLong()
        }
    }

    val id: Long by lazy { getId(this) }

    data class Space(
        val index: Int
    ): HistoryItem(TYPE_SPACE)

    data class Loader(
        val index: Int
    ): HistoryItem(TYPE_LOADER)

    data class Header(
        val title: String,
        val titleResId: Int? = null,
    ): HistoryItem(TYPE_HEADER) {

        constructor(timestamp: Long) : this(
            DateFormat.monthWithDate(timestamp)
        )

        constructor(titleResId: Int) : this(
            title = "",
            titleResId = titleResId,
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
        val position: com.tonapps.uikit.list.ListCell.Position = com.tonapps.uikit.list.ListCell.Position.SINGLE,
        val coinIconUrl: String = "",
        val fee: String? = null,
        val feeInCurrency: String? = null,
        val isOut: Boolean,
        val address: String? = null,
        val addressName: String? = null,
        val lt: Long = 0L,
    ): HistoryItem(TYPE_ACTION) {

        val hasNft: Boolean
            get() = nftImageURL != null && nftTitle != null && nftCollection != null
    }
}
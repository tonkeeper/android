package com.tonapps.tonkeeper.core.history.list.item

import android.net.Uri
import android.os.Parcelable
import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.helper.DateFormat
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.push.entities.AppPushEntity
import kotlinx.parcelize.Parcelize

sealed class HistoryItem(
    type: Int,
): BaseListItem(type), Parcelable {

    companion object {
        const val TYPE_ACTION = 1
        const val TYPE_HEADER = 2
        const val TYPE_LOADER = 3
        const val TYPE_APP = 4
    }

    val timestampForSort: Long by lazy {
        when (this) {
            is Event -> this.timestamp
            is Loader -> this.date
            is App -> this.timestamp
            else -> 0L
        }
    }

    @Parcelize
    data class Loader(
        val index: Int,
        val date: Long
    ): HistoryItem(TYPE_LOADER)

    @Parcelize
    data class Header(
        val title: String,
        val date: Long,
    ): HistoryItem(TYPE_HEADER) {

        constructor(timestamp: Long) : this(
            title = DateFormat.monthWithDate(timestamp),
            date = timestamp
        )
    }

    @Parcelize
    data class App(
        val iconUri: Uri,
        val title: String,
        val body: String,
        val date: String,
        val timestamp: Long,
        val deepLink: String
    ): HistoryItem(TYPE_APP)

    @Parcelize
    data class Event(
        val txId: String,
        val iconURL: String? = null,
        val action: ActionType,
        val title: String,
        val subtitle: String,
        val timestamp: Long = 0L,
        val comment: String? = null,
        val value: CharSequence,
        val value2: CharSequence = "",
        val currency: CharSequence? = null,
        val nftImageURL: String? = null,
        val nftTitle: String? = null,
        val nftCollection: String? = null,
        val nftAddress: String? = null,
        val tokenCode: String? = null,
        val date: String = "",
        val pending: Boolean = false,
        val position: ListCell.Position = ListCell.Position.SINGLE,
        val coinIconUrl: String = "",
        val fee: CharSequence? = null,
        val feeInCurrency: CharSequence? = null,
        val isOut: Boolean,
        val address: String? = null,
        val addressName: String? = null,
        val lt: Long = 0L,
    ): HistoryItem(TYPE_ACTION) {

        val hasNft: Boolean
            get() = nftImageURL != null && nftTitle != null && nftCollection != null
    }
}
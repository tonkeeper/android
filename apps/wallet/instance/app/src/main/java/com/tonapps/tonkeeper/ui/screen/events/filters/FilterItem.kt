package com.tonapps.tonkeeper.ui.screen.events.filters

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.dapps.entities.AppEntity

sealed class FilterItem(type: Int, open val selected: Boolean, open val id: String): BaseListItem(type) {

    companion object {
        const val TYPE_SEND = 1
        const val TYPE_RECEIVE = 2
        const val TYPE_APP = 3

        const val SEND_ID = "send"
        const val RECEIVE_ID = "receive"
    }

    data class Send(override val selected: Boolean) : FilterItem(TYPE_SEND, selected, SEND_ID)

    data class Receive(override val selected: Boolean) : FilterItem(TYPE_RECEIVE, selected, RECEIVE_ID)

    data class App(
        override val selected: Boolean,
        val app: AppEntity,
    ): FilterItem(TYPE_APP, selected, app.id) {

        val name: String
            get() = app.name

        val iconUrl: Uri
            get() = app.iconUrl.toUri()

        val url: Uri
            get() = app.url
    }

}
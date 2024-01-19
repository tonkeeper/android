package com.tonapps.singer.screen.sign.list

import android.view.ViewGroup
import com.tonapps.singer.screen.sign.list.holder.SignUnknownHolder
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class SignAdapter: BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            SignItem.UNKNOWN -> SignUnknownHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }
}
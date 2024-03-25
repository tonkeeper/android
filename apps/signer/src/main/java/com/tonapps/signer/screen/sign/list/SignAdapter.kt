package com.tonapps.signer.screen.sign.list

import android.view.ViewGroup
import com.tonapps.signer.screen.sign.list.holder.SignSendHolder
import com.tonapps.signer.screen.sign.list.holder.SignUnknownHolder

class SignAdapter: com.tonapps.uikit.list.BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when(viewType) {
            SignItem.UNKNOWN -> SignUnknownHolder(parent)
            SignItem.SEND -> SignSendHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }
}
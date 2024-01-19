package com.tonapps.singer.screen.sign.list.holder

import android.view.ViewGroup
import com.tonapps.singer.R
import com.tonapps.singer.screen.sign.list.SignItem
import uikit.list.ListCell.Companion.drawable

class SignUnknownHolder(parent: ViewGroup): SignHolder<SignItem.Unknown>(parent) {

    init {
        iconView.setImageResource(uikit.R.drawable.ic_gear_28)
        titleView.setText(R.string.unknown)
    }

    override fun onBind(item: SignItem.Unknown) {
        itemView.background = item.position.drawable(context)
    }
}
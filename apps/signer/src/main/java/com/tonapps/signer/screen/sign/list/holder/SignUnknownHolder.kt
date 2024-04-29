package com.tonapps.signer.screen.sign.list.holder

import android.view.ViewGroup
import com.tonapps.signer.R
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable

class SignUnknownHolder(parent: ViewGroup): SignHolder<SignItem.Unknown>(parent) {

    init {
        iconView.setImageResource(UIKitIcon.ic_gear_28)
        titleView.setText(R.string.unknown)
    }

    override fun onBind(item: SignItem.Unknown) {
        itemView.background = item.position.drawable(context)
    }
}
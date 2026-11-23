package com.tonapps.signer.screen.sign.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.signer.extensions.short4
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable

class SignWalletActionHolder(parent: ViewGroup): SignHolder<SignItem.WalletAction>(parent) {

    init {
        iconView.setImageResource(UIKitIcon.ic_tray_arrow_up_28)
        bodyView.visibility = View.GONE
        setDangerous(true)
    }

    override fun onBind(item: SignItem.WalletAction) {
        itemView.background = item.position.drawable(context)
        titleView.setText(item.actionType.title)

        setOptionalText(subtitleView, item.address?.short4)
        setOptionalText(amountView, item.value)
        setOptionalText(warningView, item.actionType.warning?.let(::getString))
    }
}

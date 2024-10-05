package com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable

class MyWalletHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): ContactHolder<Item.MyWallet>(parent) {

    init {
        iconView.setImageResource(UIKitIcon.ic_chevron_right_12)
    }

    override fun onBind(item: Item.MyWallet) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)

        emojiView.setEmoji(item.emoji)
        nameView.text = item.name
    }

}
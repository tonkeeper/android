package com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import uikit.extensions.drawable

class LatestHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): ContactHolder<Item.LatestContact>(parent) {

    init {
        emojiView.setEmoji("\uD83D\uDD57")
    }

    override fun onBind(item: Item.LatestContact) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)

        nameView.text = item.name
    }
}
package com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import com.tonapps.tonkeeperx.R

abstract class ContactHolder<I: Item>(parent: ViewGroup): Holder<I>(parent, R.layout.view_contact) {

    val emojiView = itemView.findViewById<EmojiView>(R.id.emoji)
    val nameView = itemView.findViewById<AppCompatTextView>(R.id.name)
    val iconView = itemView.findViewById<AppCompatImageView>(R.id.icon)

}
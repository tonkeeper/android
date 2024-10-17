package com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.Item
import com.tonapps.tonkeeperx.R

abstract class ContactHolder<I: Item>(parent: ViewGroup): Holder<I>(parent, R.layout.view_contact) {

    companion object {
        const val EDIT_ID = 1L
        const val DELETE_ID = 2L
        const val ADD_TO_CONTACTS_ID = 3L
        const val HIDE_ID = 4L
    }

    val emojiView = itemView.findViewById<EmojiView>(R.id.emoji)
    val nameView = itemView.findViewById<AppCompatTextView>(R.id.name)
    val iconView = itemView.findViewById<AppCompatImageView>(R.id.icon)

}
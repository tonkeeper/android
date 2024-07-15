package com.tonapps.tonkeeper.ui.screen.country.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.country.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable

class CountryHolder(
    parent: ViewGroup,
    private val onClick: (code: String) -> Unit
): Holder<Item.Country>(parent, R.layout.view_country) {

    private val emojiView = findViewById<AppCompatTextView>(R.id.emoji)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: Item.Country) {
        itemView.setOnClickListener { onClick(item.code) }
        itemView.background = item.position.drawable(itemView.context)
        emojiView.text = item.emoji
        titleView.text = item.name
        checkView.visibility = if (item.selected) View.VISIBLE else View.GONE
    }
}
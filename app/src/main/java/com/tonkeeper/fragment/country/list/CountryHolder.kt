package com.tonkeeper.fragment.country.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import uikit.list.BaseListHolder
import uikit.list.ListCell.Companion.drawable

class CountryHolder(
    parent: ViewGroup,
    private val onClick: (item: CountryItem) -> Unit
): BaseListHolder<CountryItem>(parent, R.layout.view_country) {

    private val emojiView = findViewById<AppCompatTextView>(R.id.emoji)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: CountryItem) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(itemView.context)
        emojiView.text = item.emoji
        titleView.text = item.title
        if (item.selected) {
            checkView.setImageResource(uikit.R.drawable.ic_donemark_thin_28)
        } else {
            checkView.setImageResource(0)
        }
    }

}

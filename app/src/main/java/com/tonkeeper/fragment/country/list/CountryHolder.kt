package com.tonkeeper.fragment.country.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import uikit.drawable.CellBackgroundDrawable
import uikit.list.BaseListHolder

class CountryHolder(
    parent: ViewGroup,
    private val onClick: (item: CountryItem) -> Unit
): BaseListHolder<CountryItem>(parent, R.layout.view_country) {

    private val emojiView = findViewById<AppCompatTextView>(R.id.emoji)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: CountryItem) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = CellBackgroundDrawable(itemView.context, item.position)
        emojiView.text = item.emoji
        titleView.text = item.title
        if (item.selected) {
            checkView.setImageResource(R.drawable.ic_donemark_thin_28)
        } else {
            checkView.setImageResource(0)
        }
    }

}

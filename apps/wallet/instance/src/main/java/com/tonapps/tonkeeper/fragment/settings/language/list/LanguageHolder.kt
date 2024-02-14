package com.tonapps.tonkeeper.fragment.settings.language.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable

class LanguageHolder(
    parent: ViewGroup,
    private val onClick: (item: LanguageItem) -> Unit
): BaseListHolder<LanguageItem>(parent, uikit.R.layout.view_item_check) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: LanguageItem) {
        itemView.background = item.position.drawable(itemView.context)
        titleView.text = item.name
        subtitleView.text = item.nameLocalized
        checkView.visibility = if (item.selected) {
            AppCompatImageView.VISIBLE
        } else {
            AppCompatImageView.GONE
        }
        itemView.setOnClickListener {
            onClick(item)
        }
    }
}
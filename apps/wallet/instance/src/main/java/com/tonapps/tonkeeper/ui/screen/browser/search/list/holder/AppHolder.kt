package com.tonapps.tonkeeper.ui.screen.browser.search.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.FrescoView

class AppHolder(
    parent: ViewGroup,
    private val onClick: (title: String, url: String) -> Unit
): Holder<Item.App>(parent, R.layout.view_browser_search_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: Item.App) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item.name, item.url) }

        iconView.setImageURI(item.icon)

        titleView.text = item.name
        subtitleView.text = item.description
    }
}
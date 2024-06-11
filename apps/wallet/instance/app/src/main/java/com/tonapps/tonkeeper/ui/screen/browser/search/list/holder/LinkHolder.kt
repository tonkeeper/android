package com.tonapps.tonkeeper.ui.screen.browser.search.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable

class LinkHolder(
    parent: ViewGroup,
    private val onClick: (title: String, url: String) -> Unit
): Holder<Item.Link>(parent, R.layout.view_browser_search_link) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: Item.Link) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item.title, item.url) }

        titleView.text = item.title
        subtitleView.text = item.url.replace("https://", "")
    }
}
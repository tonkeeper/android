package com.tonapps.tonkeeper.ui.screen.browser.search.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.navigation.Navigation

class SearchHolder(
    parent: ViewGroup,
    private val onClick: (title: String, url: String) -> Unit
): Holder<Item.Search>(parent, R.layout.view_browser_search_query) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    override fun onBind(item: Item.Search) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item.query, item.url) }
        titleView.text = item.query
    }
}
package com.tonapps.tonkeeper.ui.screen.browser.search.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.dapp.component.bindBrowserNetworkIcon
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.AsyncImageView

class AppHolder(
    parent: ViewGroup,
    private val onClick: (item: Item) -> Unit
): Holder<Item.App>(parent, R.layout.view_browser_search_app) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val networkIconView = findViewById<AsyncImageView>(R.id.network_icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: Item.App) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item) }

        iconView.setImageURI(item.icon)
        networkIconView.bindBrowserNetworkIcon(item.app.chains)

        titleView.text = item.name
        subtitleView.text = item.description
    }
}
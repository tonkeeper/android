package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper.openDApp
import com.tonapps.dapp.component.bindBrowserNetworkIcon
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.extensions.drawable
import uikit.widget.AsyncImageView
import uikit.widget.ResizeOptions

class ExploreAppExploreHolder(parent: ViewGroup): ExploreHolder<ExploreItem.App>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val networkIconView = findViewById<AsyncImageView>(R.id.network_icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    init {
        val placeholderDrawable = context.drawable(uikit.R.drawable.bg_content_tint_16)
        placeholderDrawable.setTint(context.backgroundContentTintColor)
        iconView.setPlaceholder(placeholderDrawable)
    }

    override fun onBind(item: ExploreItem.App) {
        itemView.setOnClickListener {
            item.app.openDApp(context, item.wallet, "browser", item.country, item.multichain)
        }
        iconView.setImageURIWithResize(item.icon, ResizeOptions.forSquareSize(172))
        networkIconView.bindBrowserNetworkIcon(item.app.chains)
        nameView.text = item.name
        if (item.singleLine) {
            nameView.isSingleLine = true
            nameView.maxLines = 1
        } else {
            nameView.isSingleLine = false
            nameView.maxLines = 2
        }
    }
}
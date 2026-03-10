package com.tonapps.tonkeeper.ui.screen.browser.more.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper.openDApp
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable
import uikit.widget.AsyncImageView
import uikit.widget.ResizeOptions

class Holder(parent: ViewGroup): BaseListHolder<Item>(parent, R.layout.view_browser_full_app) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            item.app.openDApp(context, item.wallet, "browser_all", item.country)
        }

        iconView.setImageURIWithResize(item.icon, ResizeOptions.forSquareSize(128))
        titleView.text = item.name
        subtitleView.text = item.description
    }
}
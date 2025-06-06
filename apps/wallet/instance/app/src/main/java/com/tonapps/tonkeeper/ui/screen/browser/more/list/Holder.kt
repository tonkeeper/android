package com.tonapps.tonkeeper.ui.screen.browser.more.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.helper.BrowserHelper.openDApp
import com.tonapps.tonkeeper.koin.installId
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.base.BaseFragment
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class Holder(parent: ViewGroup): BaseListHolder<Item>(parent, R.layout.view_browser_full_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            item.app.openDApp(context, item.wallet, "browser_all", item.country)
        }

        iconView.setImageURIWithResize(item.icon, ResizeOptions.forSquareSize(128)!!)
        titleView.text = item.name
        subtitleView.text = item.description
    }
}
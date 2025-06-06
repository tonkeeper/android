package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.banners

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
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class BannerHolder(parent: ViewGroup): BaseListHolder<BannerAppItem>(parent, R.layout.view_browser_app_banner) {

    private val bgView = findViewById<FrescoView>(R.id.bg)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)

    override fun onBind(item: BannerAppItem) {
        itemView.setOnClickListener {
            item.app.openDApp(context, item.wallet, "banner", item.country)
        }

        bgView.setImageURI(item.poster, ResizeOptions.forSquareSize(712))
        iconView.setImageURI(item.icon, ResizeOptions.forSquareSize(128))

        titleView.setTextColor(item.textColor)
        titleView.text = item.name

        descriptionView.setTextColor(item.textColor)
        descriptionView.text = item.description
    }

}
package com.tonapps.tonkeeper.ui.screen.browser.explore.banners

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
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
            Navigation.from(context)?.add(DAppScreen.newInstance(item.name, item.host, item.url.toString()))
        }

        bgView.setImageURI(item.poster)
        iconView.setImageURI(item.icon)

        titleView.setTextColor(item.textColor)
        titleView.text = item.name

        descriptionView.setTextColor(item.textColor)
        descriptionView.text = item.description
    }

}
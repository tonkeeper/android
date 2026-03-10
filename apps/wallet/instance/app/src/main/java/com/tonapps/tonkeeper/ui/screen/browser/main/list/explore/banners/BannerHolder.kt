package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.banners

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import com.tonapps.tonkeeper.helper.BrowserHelper.openDApp
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.widget.AsyncImageView
import uikit.widget.ResizeOptions

class BannerHolder(parent: ViewGroup): BaseListHolder<BannerAppItem>(parent, R.layout.view_browser_app_banner) {

    private val bgView = findViewById<AsyncImageView>(R.id.bg)
    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)

    override fun onBind(item: BannerAppItem) {
        itemView.setOnClickListener {
            item.app.openDApp(context, item.wallet, "banner", item.country)
        }

        bgView.doOnLayout {
            bgView.setImageURI(item.poster, ResizeOptions(it.width, it.height))
        }

        iconView.setImageURI(item.icon, ResizeOptions.forSquareSize(128))

        titleView.setTextColor(item.textColor)
        titleView.text = item.name

        descriptionView.setTextColor(item.textColor)
        descriptionView.text = item.description
    }

}
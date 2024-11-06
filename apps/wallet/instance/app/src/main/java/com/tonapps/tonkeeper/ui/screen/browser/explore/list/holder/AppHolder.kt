package com.tonapps.tonkeeper.ui.screen.browser.explore.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.ui.screen.browser.explore.list.Item
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class AppHolder(parent: ViewGroup): Holder<Item.App>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: Item.App) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(DAppScreen.newInstance(item.wallet, item.name, item.url))
        }
        iconView.setImageURI(item.icon, ResizeOptions.forSquareSize(64))
        nameView.text = item.name
    }
}
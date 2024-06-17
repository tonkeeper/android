package com.tonapps.tonkeeper.ui.screen.browser.connected.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class AppHolder(
    parent: ViewGroup,
    private val onLongClick: (DAppEntity) -> Unit
): BaseListHolder<Item>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: Item) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(DAppScreen.newInstance(item.name, item.host, item.url.toString()))
        }
        itemView.setOnLongClickListener {
            onLongClick(item.app)
            true
        }
        iconView.setImageURI(item.icon)
        nameView.text = item.name
    }

}
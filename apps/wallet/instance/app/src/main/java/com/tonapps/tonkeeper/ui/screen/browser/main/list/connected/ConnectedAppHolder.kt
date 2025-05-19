package com.tonapps.tonkeeper.ui.screen.browser.main.list.connected

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.data.dapps.entities.AppEntity
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class ConnectedAppHolder(
    parent: ViewGroup,
    private val onLongClick: (AppEntity) -> Unit
): BaseListHolder<ConnectedItem>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: ConnectedItem) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(DAppScreen.newInstance(
                wallet = item.wallet,
                title = item.name,
                url = item.url,
                iconUrl = item.icon.toString(),
                source = "browser_connected"
            ))
        }
        itemView.setOnLongClickListener {
            onLongClick(item.app)
            true
        }
        iconView.setImageURI(item.icon)
        nameView.text = item.name
    }

}
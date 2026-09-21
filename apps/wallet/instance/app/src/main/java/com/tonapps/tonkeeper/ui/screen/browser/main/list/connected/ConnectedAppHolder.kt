package com.tonapps.tonkeeper.ui.screen.browser.main.list.connected

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.dapp.component.bindBrowserNetworkIcon
import com.tonapps.tonkeeper.koin.environment
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappBrowserAnalytics
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.navigation.Navigation
import uikit.widget.AsyncImageView

class ConnectedAppHolder(
    parent: ViewGroup,
    private val onLongClick: (ConnectedItem) -> Unit
): BaseListHolder<ConnectedItem>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val networkIconView = findViewById<AsyncImageView>(R.id.network_icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: ConnectedItem) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(
                DAppScreen.newInstance(
                    wallet = item.wallet,
                    title = item.name,
                    url = item.url,
                    iconUrl = item.icon.toString(),
                    source = "browser_connected",
                    analytics = DappBrowserAnalytics.directContext(
                        source = "browser_connected",
                        url = item.url,
                        chain = item.chain,
                        country = context.environment?.deviceCountry
                    )
                )
            )
        }
        itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
        iconView.setImageURI(item.icon)
        networkIconView.bindBrowserNetworkIcon(item.chain)
        nameView.text = item.name
    }

}

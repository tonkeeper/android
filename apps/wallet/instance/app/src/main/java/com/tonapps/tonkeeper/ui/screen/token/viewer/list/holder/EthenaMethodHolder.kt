package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import com.tonapps.extensions.uri
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.AsyncImageView

class EthenaMethodHolder(parent: ViewGroup) :
    Holder<Item.EthenaMethod>(parent, R.layout.view_ethena_method) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val apyView = findViewById<AppCompatTextView>(R.id.apy)
    private val openButtonView = findViewById<View>(R.id.button_open)

    override fun onBind(item: Item.EthenaMethod) {
        itemView.background = item.position.drawable(context)

        itemView.setOnClickListener { openDapp(item) }
        openButtonView.setOnClickListener { openDapp(item) }

        titleView.text = getString(Localization.deposit_and_stake)
        apyView.text = context.getString(Localization.ethena_operated_by, item.name)
        item.iconRes?.let { iconView.setLocalRes(it) }
    }

    private fun openDapp(item: Item.EthenaMethod) {
        context.navigation?.add(
            DAppScreen.newInstance(
                wallet = item.wallet,
                title = item.name,
                url = item.url.toUri(),
                iconUrl = item.iconRes?.uri().toString(),
                source = "token_screen",
                forceConnect = true
            )
        )
    }

}
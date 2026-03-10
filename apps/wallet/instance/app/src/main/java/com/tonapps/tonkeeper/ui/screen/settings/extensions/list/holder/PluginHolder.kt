package com.tonapps.tonkeeper.ui.screen.settings.extensions.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.short8
import com.tonapps.tonkeeper.ui.screen.settings.extensions.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeper.ui.screen.send.boc.RemoveExtensionScreen
import uikit.navigation.Navigation.Companion.navigation

class PluginHolder(
    parent: ViewGroup,
) : Holder<Item.Plugin>(parent, R.layout.view_settings_extension) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val disableButton = findViewById<Button>(R.id.disable)

    override fun onBind(item: Item.Plugin) {
        itemView.background = item.position.drawable(context)
        titleView.text = item.plugin.type
        subtitleView.text =
            item.plugin.address.toUserFriendly(wallet = false, testnet = item.wallet.testnet).short8
        val isLegacySubscription = item.plugin.type == "subscription_v1"
        disableButton.visibility = if (isLegacySubscription) View.VISIBLE else View.GONE
        disableButton.setOnClickListener {
            context.navigation?.add(
                RemoveExtensionScreen.newInstance(
                    item.wallet,
                    item.plugin.address
                )
            )
        }
    }
}




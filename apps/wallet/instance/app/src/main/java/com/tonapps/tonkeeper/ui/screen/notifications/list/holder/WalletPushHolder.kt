package com.tonapps.tonkeeper.ui.screen.notifications.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.notifications.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.extensions.drawable
import uikit.widget.SwitchView

class WalletPushHolder(
    parent: ViewGroup,
    private val onWalletCallback: (WalletEntity, Boolean) -> Unit
): Holder<Item.Wallet>(parent, R.layout.view_notifications_wallet) {

    private val switchView = findViewById<SwitchView>(R.id.push)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
        itemView.setOnClickListener {
            switchView.toggle(true)
        }
    }

    override fun onBind(item: Item.Wallet) {
        switchView.setChecked(item.pushEnabled, false)
        switchView.doCheckedChanged = { checked, byUser ->
            if (byUser) {
                onWalletCallback(item.wallet, checked)
            }
        }
    }
}
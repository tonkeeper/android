package com.tonkeeper.fragment.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.tonkeeper.R
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.openCamera
import com.tonkeeper.extensions.receive
import com.tonkeeper.extensions.sendCoin
import com.tonkeeper.fragment.wallet.main.list.item.WalletActionItem
import uikit.navigation.Navigation.Companion.navigation

class WalletActionsHolder(
    parent: ViewGroup
): WalletHolder<WalletActionItem>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)

    init {
        sendView.setOnClickListener { nav?.sendCoin() }
        receiveView.setOnClickListener { nav?.receive() }
        buyOrSellView.setOnClickListener { FiatDialog.open(context) }
    }

    override fun onBind(item: WalletActionItem) {

    }

}

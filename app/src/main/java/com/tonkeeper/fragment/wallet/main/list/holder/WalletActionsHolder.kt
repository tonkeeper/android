package com.tonkeeper.fragment.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.receive
import com.tonkeeper.extensions.sendCoin
import com.tonkeeper.fragment.fiat.modal.FiatModalFragment
import com.tonkeeper.fragment.wallet.main.list.item.WalletActionItem

class WalletActionsHolder(
    parent: ViewGroup
): WalletHolder<WalletActionItem>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)

    init {
        sendView.setOnClickListener { nav?.sendCoin() }
        receiveView.setOnClickListener { nav?.receive() }
        buyOrSellView.setOnClickListener {
            // nav?.add(FiatModalFragment.newInstance())
            FiatDialog.open(context)
        }
    }

    override fun onBind(item: WalletActionItem) {

    }

}

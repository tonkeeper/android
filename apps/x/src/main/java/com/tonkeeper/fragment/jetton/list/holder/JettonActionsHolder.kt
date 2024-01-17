package com.tonkeeper.fragment.jetton.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonkeeper.api.address
import com.tonkeeper.extensions.receive
import com.tonkeeper.extensions.sendCoin
import com.tonkeeper.fragment.jetton.list.JettonItem
import ton.wallet.WalletType

class JettonActionsHolder(
    parent: ViewGroup
): JettonHolder<JettonItem.Actions>(parent, R.layout.view_jetton_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)

    override fun onBind(item: JettonItem.Actions) {
        sendView.setOnClickListener { nav?.sendCoin(
            jettonAddress = item.jetton.address,
        ) }

        sendView.visibility = if (item.walletType == WalletType.Watch) {
            View.GONE
        } else {
            View.VISIBLE
        }

        receiveView.setOnClickListener { nav?.receive(
            item.jetton,
        ) }
    }

}
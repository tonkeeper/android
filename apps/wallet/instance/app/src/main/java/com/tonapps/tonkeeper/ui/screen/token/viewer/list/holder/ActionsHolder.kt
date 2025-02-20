package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation

class ActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_token_actions) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val swapView = findViewById<View>(R.id.swap)

    override fun onBind(item: Item.Actions) {
        sendView.isEnabled = item.send
        sendView.setOnClickListener {
            navigation?.add(SendScreen.newInstance(
                wallet = item.wallet,
                tokenAddress = item.tokenAddress,
                type = SendScreen.Companion.Type.Default
            ))
        }
        receiveView.setOnClickListener {
            navigation?.add(QRScreen.newInstance(item.wallet, item.token))
        }
        swapView.isEnabled = item.swap
        swapView.setOnClickListener {
            navigation?.add(SwapScreen.newInstance(item.wallet, item.swapUri, item.walletAddress, item.tokenAddress))
        }
    }
}
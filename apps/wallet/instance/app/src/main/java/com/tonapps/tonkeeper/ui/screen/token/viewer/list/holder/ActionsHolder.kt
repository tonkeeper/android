package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation
import uikit.widget.ButtonsLayout

class ActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_token_actions) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val stakeView = findViewById<View>(R.id.stake)
    private val swapView = findViewById<View>(R.id.swap)
    private val buttonsView = findViewById<ButtonsLayout>(R.id.buttons)

    override fun onBind(item: Item.Actions) {
        buttonsView.maxColumnCount = item.maxColumnCount
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
            if (item.swapMethod != null) {
                BrowserHelper.openPurchase(context, item.swapMethod)
            } else {
                navigation?.add(SwapScreen.newInstance(item.wallet, item.swapUri, item.walletAddress, item.tokenAddress))
            }
        }

        if (item.stakeApp == null) {
            stakeView.visibility = View.GONE
        } else {
            stakeView.visibility = View.VISIBLE
            stakeView.setOnClickListener {
                navigation?.add(DAppScreen.newInstance(item.wallet, item.stakeApp, "token"))
            }
        }
    }
}
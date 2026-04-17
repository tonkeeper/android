package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.tonapps.bus.generated.Events
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreen
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreenType
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation
import uikit.widget.ButtonsLayout

class ActionsHolder(parent: ViewGroup) : Holder<Item.Actions>(parent, R.layout.view_token_actions) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val swapView = findViewById<View>(R.id.swap)
    private val buttonsView = findViewById<ButtonsLayout>(R.id.buttons)

    override fun onBind(item: Item.Actions) {
        buttonsView.maxColumnCount = item.maxColumnCount
        sendView.isEnabled = item.send
        sendView.setOnClickListener {
            if (item.tronTransfersDisabled) {
                navigation?.add(
                    TronFeesScreen.newInstance(
                        wallet = item.wallet,
                        type = TronFeesScreenType.InsufficientBalance
                    )
                )
            } else {
                navigation?.add(
                    SendScreen.newInstance(
                        wallet = item.wallet,
                        tokenAddress = item.tokenAddress,
                        type = SendScreen.Companion.Type.Default,
                        from = Events.SendNative.SendNativeFrom.JettonScreen
                    )
                )
            }
        }
        receiveView.setOnClickListener {
            navigation?.add(QrAssetFragment.newInstance(item.token))
        }

        swapView.isVisible = item.swap
        swapView.setOnClickListener {
            if (item.tronSwapUrl != null) {
                BrowserHelper.open(context, item.tronSwapUrl)
            } else {
                val fragment = SwapScreen.newInstance(
                    wallet = item.wallet,
                    fromToken = item.currency,
                    nativeSwap = context.serverFlags?.disableNativeSwap != true,
                    uri = item.swapUri
                )
                navigation?.add(fragment)
            }
        }
    }
}
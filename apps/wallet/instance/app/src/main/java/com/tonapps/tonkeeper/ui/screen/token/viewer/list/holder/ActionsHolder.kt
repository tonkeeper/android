package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.swap.SwapFragment
import com.tonapps.swap.SwapRoutes
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreen
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreenType
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAssets
import uikit.navigation.Navigation
import uikit.widget.ButtonsLayout
import uikit.extensions.setMoonOnClickListener

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
        sendView.setMoonOnClickListener {
            if (item.tronTransfersDisabled) {
                navigation?.add(
                    TronFeesScreen.newInstance(
                        wallet = item.wallet,
                        analyticsFrom = DepositFlowFrom.JettonScreen,
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
        receiveView.setMoonOnClickListener {
            navigation?.add(QrAssetFragment.newInstance(item.token, analyticsFrom = DepositFlowFrom.JettonScreen))
        }

        swapView.isVisible = item.swap
        swapView.setMoonOnClickListener {
            if (item.legacyTronSwap) {
                // The opened USDT goes on the send side, as on every other token page.
                val route = SwapRoutes.Swap(
                    sellAssetId = LegacySwapAssets.USDT_TRC20_ASSET_ID,
                    buyAssetId = LegacySwapAssets.TON_ASSET_ID,
                )
                navigation?.add(SwapFragment.create(initial = route))
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
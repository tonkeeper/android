package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet

class ActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)
    private val swapView = findViewById<View>(R.id.swap)
    private val scanView = findViewById<View>(R.id.scan)
    private val stakeView = findViewById<View>(R.id.stake)

    init {
        scanView.setOnClickListener { navigation?.add(CameraScreen.newInstance()) }
    }

    override fun onBind(item: Item.Actions) {
        receiveView.setOnClickListener {
            navigation?.add(QRScreen.newInstance(item.wallet, item.token))
        }
        swapView.setOnClickListener {
            navigation?.add(SwapScreen.newInstance(item.wallet, item.swapUri, item.address, TokenEntity.TON.address))
        }
        buyOrSellView.setOnClickListener {
            navigation?.add(PurchaseScreen.newInstance(item.wallet, "wallet"))
        }
        sendView.setOnClickListener {
            navigation?.add(SendScreen.newInstance(item.wallet, type = SendScreen.Companion.Type.Default))
        }
        stakeView.setOnClickListener {
            navigation?.add(StakingScreen.newInstance(item.wallet))
        }

        val isSwapDisable = context.remoteConfig?.isSwapDisable == true
        val isStakingDisable = context.remoteConfig?.isStakingDisable == true

        swapView.isEnabled = item.walletType != Wallet.Type.Watch && !isSwapDisable
        sendView.isEnabled = item.walletType != Wallet.Type.Watch
        scanView.isEnabled = item.walletType != Wallet.Type.Watch
        stakeView.isEnabled = item.walletType != Wallet.Type.Watch && item.walletType != Wallet.Type.Testnet && !isStakingDisable
        buyOrSellView.isEnabled = item.walletType != Wallet.Type.Testnet

        if (isSwapDisable) {
            swapView.alpha = 0f
        }
        if (isStakingDisable) {
            stakeView.alpha = 0f
        }
    }

}
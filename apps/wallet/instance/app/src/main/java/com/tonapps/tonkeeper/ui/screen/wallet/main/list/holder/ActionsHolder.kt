package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.extensions.openCamera
import com.tonapps.tonkeeper.ui.screen.purchase.main.PurchaseScreen
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
        scanView.setOnClickListener { navigation?.openCamera() }
        stakeView.setOnClickListener { navigation?.add(StakingScreen.newInstance()) }
    }

    override fun onBind(item: Item.Actions) {
        receiveView.setOnClickListener {
            navigation?.add(QRScreen.newInstance(item.address, item.token, item.walletType))
        }
        swapView.setOnClickListener {
            navigation?.add(SwapScreen.newInstance(item.swapUri, item.address, TokenEntity.TON.address))
        }
        buyOrSellView.setOnClickListener {
            navigation?.add(PurchaseScreen.newInstance(item.wallet))
        }
        sendView.setOnClickListener {
            navigation?.add(SendScreen.newInstance(item.wallet))
        }

        swapView.isEnabled = item.walletType != Wallet.Type.Watch && !item.disableSwap
        sendView.isEnabled = item.walletType != Wallet.Type.Watch
        scanView.isEnabled = item.walletType != Wallet.Type.Watch
        stakeView.isEnabled = item.walletType != Wallet.Type.Watch && item.walletType != Wallet.Type.Testnet
        buyOrSellView.isEnabled = item.walletType != Wallet.Type.Testnet && !item.disableSwap
    }

}
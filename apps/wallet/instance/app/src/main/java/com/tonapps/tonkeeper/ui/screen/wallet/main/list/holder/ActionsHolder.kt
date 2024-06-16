package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.openCamera
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.SendScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import uikit.navigation.Navigation

class ActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)
    private val swapView = findViewById<View>(R.id.swap)
    private val scanView = findViewById<View>(R.id.scan)
    private val navigation = Navigation.from(context)

    init {
        sendView.setOnClickListener { navigation?.add(SendScreen.newInstance()) }
        buyOrSellView.setOnClickListener { FiatDialog.open(context) }
        scanView.setOnClickListener { navigation?.openCamera() }
    }

    override fun onBind(item: Item.Actions) {
        receiveView.setOnClickListener {
            navigation?.add(QRScreen.newInstance(item.address, item.token, item.walletType))
        }
        swapView.setOnClickListener {
            navigation?.add(SwapScreen.newInstance(item.swapUri, item.address, TokenEntity.TON.address))
        }

        swapView.isEnabled = item.walletType == Wallet.Type.Default && !item.disableSwap
        sendView.isEnabled = item.walletType != Wallet.Type.Watch
        scanView.isEnabled = item.walletType != Wallet.Type.Watch
        buyOrSellView.isEnabled = item.walletType != Wallet.Type.Testnet && !item.disableSwap
    }

}
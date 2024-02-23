package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.openCamera
import com.tonapps.tonkeeper.extensions.receive
import com.tonapps.tonkeeper.extensions.sendCoin
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletActionItem
import ton.wallet.WalletType
import uikit.navigation.Navigation

class WalletActionsHolder(
    parent: ViewGroup
): WalletHolder<WalletActionItem>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)
    private val scanView = findViewById<View>(R.id.scan)

    init {
        sendView.setOnClickListener { Navigation.from(context)?.sendCoin() }
        receiveView.setOnClickListener { Navigation.from(context)?.receive() }
        buyOrSellView.setOnClickListener {
            // nav?.add(FiatModalFragment.newInstance())
            FiatDialog.open(context)
        }

        scanView.setOnClickListener {
            Navigation.from(context)?.openCamera()
        }
    }

    override fun onBind(item: WalletActionItem) {
        sendView.isEnabled = item.walletType != WalletType.Watch
        buyOrSellView.isEnabled = item.walletType != WalletType.Testnet && item.walletType != WalletType.Watch
    }

}

package com.tonapps.tonkeeper.fragment.chart.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.receive
import com.tonapps.tonkeeper.extensions.sendCoin
import com.tonapps.tonkeeper.fragment.chart.list.ChartItem
import ton.wallet.WalletType
import uikit.navigation.Navigation

class ChartActionsHolder(
    parent: ViewGroup
): ChartHolder<ChartItem.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)

    init {
        sendView.setOnClickListener { Navigation.from(context)?.sendCoin() }
        receiveView.setOnClickListener { Navigation.from(context)?.receive() }
        buyOrSellView.setOnClickListener { FiatDialog.open(context) }
    }

    override fun onBind(item: ChartItem.Actions) {
        sendView.visibility = if (item.walletType == WalletType.Watch) {
            View.GONE
        } else {
            View.VISIBLE
        }

        buyOrSellView.visibility = if (item.walletType == WalletType.Testnet) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

}
package com.tonapps.tonkeeper.fragment.chart.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateMargins
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.sendCoin
import com.tonapps.tonkeeper.fragment.chart.list.ChartItem
import com.tonapps.wallet.data.account.WalletType
import uikit.navigation.Navigation

class ChartActionsHolder(
    parent: ViewGroup
): ChartHolder<ChartItem.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)

    init {
        val offsetVertical = context.resources.getDimensionPixelSize(uikit.R.dimen.offsetMedium)
        (itemView.layoutParams as RecyclerView.LayoutParams).updateMargins(top = offsetVertical, bottom = offsetVertical)
        sendView.setOnClickListener { Navigation.from(context)?.sendCoin() }
        // receiveView.setOnClickListener { Navigation.from(context)?.receive() }
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
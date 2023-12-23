package com.tonkeeper.fragment.chart.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.receive
import com.tonkeeper.extensions.sendCoin
import com.tonkeeper.fragment.chart.list.ChartItem
import com.tonkeeper.fragment.receive.ReceiveScreen

class ChartActionsHolder(
    parent: ViewGroup
): ChartHolder<ChartItem.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)
    private val buyOrSellView = findViewById<View>(R.id.buy_or_sell)

    init {
        sendView.setOnClickListener { nav?.sendCoin() }
        receiveView.setOnClickListener { nav?.receive() }
        buyOrSellView.setOnClickListener { FiatDialog.open(context) }
    }

    override fun onBind(item: ChartItem.Actions) {

    }

}
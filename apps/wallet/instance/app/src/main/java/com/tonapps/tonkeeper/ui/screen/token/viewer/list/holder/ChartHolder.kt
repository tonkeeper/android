package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.view.ChartView
import com.tonapps.tonkeeperx.R

class ChartHolder(parent: ViewGroup): Holder<Item.Chart>(parent, R.layout.view_token_chart) {

    private val chartView = findViewById<ChartView>(R.id.chart)

    override fun onBind(item: Item.Chart) {
        chartView.setData(item.data, item.square)
    }

}
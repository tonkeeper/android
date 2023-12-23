package com.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.extensions.colorForChange
import com.tonkeeper.fragment.chart.list.ChartItem

class ChartPriceHolder(
    parent: ViewGroup
): ChartHolder<ChartItem.Price>(parent, R.layout.view_chart_price) {

    private val priceView = findViewById<AppCompatTextView>(R.id.price)
    private val diffView = findViewById<AppCompatTextView>(R.id.diff)

    override fun onBind(item: ChartItem.Price) {
        priceView.text = item.rateFormat
        diffView.text = item.rate24h
        diffView.setTextColor(getColor(item.rate24h.colorForChange))
    }

}

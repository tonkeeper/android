package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.extensions.getDiffColor
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R

class PriceHolder(parent: ViewGroup): Holder<Item.Price>(parent, R.layout.view_token_price) {

    private val priceView = findViewById<AppCompatTextView>(R.id.price)
    private val diffView = findViewById<AppCompatTextView>(R.id.diff)

    override fun onBind(item: Item.Price) {
        priceView.text = item.fiatPrice.withCustomSymbol(context)
        diffView.text = context.buildRateString(item.rateDiff24h, "").withCustomSymbol(context)
        diffView.setTextColor(context.getDiffColor(item.rateDiff24h.toString()))
    }

}
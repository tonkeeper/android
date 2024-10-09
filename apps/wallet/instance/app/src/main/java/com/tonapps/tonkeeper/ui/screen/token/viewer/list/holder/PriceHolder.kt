package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.text.SpannableStringBuilder
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.color
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.extensions.getDiffColor
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.withAlpha
import uikit.extensions.withGreenBadge

class PriceHolder(parent: ViewGroup): Holder<Item.Price>(parent, R.layout.view_token_price) {

    private val priceView = findViewById<AppCompatTextView>(R.id.price)
    private val diffView = findViewById<AppCompatTextView>(R.id.diff)

    override fun onBind(item: Item.Price) {
        priceView.text = item.fiatPrice.withCustomSymbol(context)

        val diffColor = context.getDiffColor(item.rateDiff24h.toString())
        val diffBuilder = SpannableStringBuilder(context.buildRateString(item.rateDiff24h, "").withCustomSymbol(context))
        if (item.delta.isNotBlank()) {
            diffBuilder.append(" ").color(diffColor.withAlpha(.44f)) { append(item.delta) }
        }

        diffView.text = diffBuilder
        diffView.setTextColor(diffColor)
    }

}
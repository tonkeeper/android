package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.tonkeeper.fragment.chart.ChartScreen
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletTonCellItem
import uikit.navigation.Navigation

class WalletCellTonHolder(
    parent: ViewGroup
): WalletCellHolder<WalletTonCellItem>(parent, R.layout.view_cell_ton) {

    private val rateView = findViewById<TextView>(R.id.rate)
    private val balanceView = findViewById<TextView>(R.id.balance)
    private val balanceCurrencyView = findViewById<TextView>(R.id.balance_currency)

    init {
        itemView.setOnClickListener { Navigation.from(context)?.add(ChartScreen.newInstance()) }
    }

    override fun onBind(item: WalletTonCellItem) {
        rateView.text = createRate(item.rate, item.rateDiff24h)
        balanceView.text = item.balance
        balanceCurrencyView.text = item.balanceCurrency
    }

    private fun createRate(rate: String, diff24h: String): SpannableString {
        val span = SpannableString("$rate $diff24h")
        span.setSpan(
            ForegroundColorSpan(getDiffColor(diff24h)),
            rate.length,
            rate.length + diff24h.length + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return span
    }

    @ColorInt
    private fun getDiffColor(diff: String): Int {
        val resId = when {
            diff.startsWith("-") -> UIKitColor.accentRed
            diff.startsWith("+") -> UIKitColor.accentGreen
            else -> UIKitColor.textSecondary
        }
        return context.getColor(resId)
    }

}

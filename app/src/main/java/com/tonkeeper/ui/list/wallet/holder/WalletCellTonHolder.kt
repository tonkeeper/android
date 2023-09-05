package com.tonkeeper.ui.list.wallet.holder

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import com.tonkeeper.R
import com.tonkeeper.ui.list.wallet.item.WalletTonCellItem

class WalletCellTonHolder(
    parent: ViewGroup
): WalletCellHolder<WalletTonCellItem>(parent, R.layout.view_cell_ton) {

    private val rateView = findViewById<TextView>(R.id.rate)
    private val balanceView = findViewById<TextView>(R.id.balance)
    private val balanceUSDView = findViewById<TextView>(R.id.balance_usd)

    override fun onBind(item: WalletTonCellItem) {
        rateView.text = createRate(item.rate, item.rateDiff24h)
        balanceView.text = item.balance
        balanceUSDView.text = item.balanceUSD
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
            diff.startsWith("-") -> R.color.red
            diff.startsWith("+") -> R.color.green
            else -> R.color.secondary
        }
        return context.getColor(resId)
    }

}
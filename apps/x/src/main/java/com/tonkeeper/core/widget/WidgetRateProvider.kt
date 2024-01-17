package com.tonkeeper.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.formatter.CurrencyFormatter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedTokens
import uikit.extensions.withAlpha
import java.util.Calendar
import java.util.Locale

class WidgetRateProvider: Widget() {

    companion object {
        fun requestUpdate(context: Context = App.instance) {
            update(context, WidgetRateProvider::class.java)
        }
    }

    private data class Rate(
        val diff24h: String,
        val diff7d: String,
        val price: String,
        val date: String
    )

    @OptIn(DelicateCoroutinesApi::class)
    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        scope.launch(Dispatchers.IO) {
            val (accountId, testnet) = getAccountId()
            val diff24h = currencyManager.getRate24h(accountId, testnet, SupportedTokens.TON, currency)
            val diff7d = currencyManager.getRate7d(accountId, testnet, SupportedTokens.TON, currency)
            val price = currencyManager.getRate(accountId, testnet, SupportedTokens.TON, currency)
            val date = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
            val rate = Rate(diff24h, diff7d, CurrencyFormatter.formatRate(currency.code, price), date)

            displayData(context, manager, id, rate)
        }
    }

    private suspend fun getAccountId(): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext Pair("", false)
        return@withContext Pair(wallet.accountId, wallet.testnet)
    }

    private fun getDiffColor(value: String): Int {
        val hex = when {
            value.startsWith("+") -> "#39CC83"
            value.startsWith("-") -> "#FF4766"
            else -> "#45AEF5"
        }
        return Color.parseColor(hex)
    }

    private suspend fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        rate: Rate
    ) = withContext(Dispatchers.Main) {
        val diff = SpannableString(rate.diff24h + " " + rate.diff7d)
        diff.setSpan(
            ForegroundColorSpan(getDiffColor(rate.diff24h)),
            0,
            rate.diff24h.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        diff.setSpan(
            ForegroundColorSpan(getDiffColor(rate.diff7d).withAlpha(.44f)),
            rate.diff24h.length + 1,
            rate.diff24h.length + 1 + rate.diff7d.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val removeView = RemoteViews(context.packageName, R.layout.widget_rate)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.update_date, context.getString(R.string.widget_updated, rate.date))
        removeView.setTextViewText(R.id.price, rate.price)
        removeView.setTextViewText(R.id.diff, diff)
        manager.updateAppWidget(id, removeView)
    }

}
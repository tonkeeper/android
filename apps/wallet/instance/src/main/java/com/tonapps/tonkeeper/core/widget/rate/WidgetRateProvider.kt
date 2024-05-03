package com.tonapps.tonkeeper.core.widget.rate

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.extensions.getDiffColor
import com.tonapps.tonkeeper.koin.koin
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.withAlpha
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetRateProvider: Widget() {

    companion object {
        fun requestUpdate(context: Context = com.tonapps.tonkeeper.App.instance) {
            update(context, WidgetRateProvider::class.java)
        }
    }

    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val koin = context.koin ?: return
        val scope = koin.get<CoroutineScope>()
        val ratesRepository = koin.get<RatesRepository>()
        val settingsRepository = koin.get<SettingsRepository>()
        scope.launch(Dispatchers.IO) {
            val date = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
            val tokenCode = TokenEntity.TON.symbol
            val rates = ratesRepository.getRates(settingsRepository.currency, tokenCode)
            val priceFormat = CurrencyFormatter.formatFiat(settingsRepository.currency.code, rates.getRate(tokenCode))
            val entity = WidgetRateEntity(
                diff24h = rates.getDiff24h(tokenCode),
                diff7d = rates.getDiff7d(tokenCode),
                price = priceFormat,
                date = date
            )
            displayData(context, manager, id, entity)
        }
    }

    private suspend fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        entity: WidgetRateEntity
    ) = withContext(Dispatchers.Main) {
        val diff = SpannableString(entity.diff24h + " " + entity.diff7d)
        diff.setSpan(
            ForegroundColorSpan(context.getDiffColor(entity.diff24h)),
            0,
            entity.diff24h.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        diff.setSpan(
            ForegroundColorSpan(context.getDiffColor(entity.diff7d).withAlpha(.44f)),
            entity.diff24h.length + 1,
            entity.diff24h.length + 1 + entity.diff7d.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val removeView = RemoteViews(context.packageName, R.layout.widget_rate)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.update_date, context.getString(Localization.widget_updated, entity.date))
        removeView.setTextViewText(R.id.price, entity.price)
        removeView.setTextViewText(R.id.diff, diff)
        manager.updateAppWidget(id, removeView)
    }

}
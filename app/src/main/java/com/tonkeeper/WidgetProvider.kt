package com.tonkeeper

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.widget.RemoteViews
import ton.console.method.RatesMethod
import ton.console.model.RatesModel
import java.util.Calendar
import java.util.Locale


class WidgetProvider: AppWidgetProvider() {

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val uiHandler = Handler(context.mainLooper)
        Thread {
            val rates = RatesMethod().execute()
            uiHandler.post {
                displayData(context, manager, id, rates)
            }
        }.start()
    }

    private fun displayData(context: Context, manager: AppWidgetManager, id: Int, ratesModel: RatesModel) {
        try {
            val ton = ratesModel.ton
            val currency = App.settings.currency
            val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

            val removeView = RemoteViews(context.packageName, R.layout.widget)
            removeView.setTextViewText(R.id.diff24h, ton.diff24h.get(currency))
            removeView.setTextViewText(R.id.rate, "1 TON = " + ton.prices.getAmount(currency).toUserLike())
            removeView.setTextViewText(R.id.last_update, "Last update " + df.format(Calendar.getInstance().time))
            manager.updateAppWidget(id, removeView)
        } catch (ignored: Throwable) {}
    }


    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        for (id in ids) {
            update(context, manager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        super.onRestored(context, oldIds, newIds)
    }

    override fun peekService(myContext: Context, service: Intent): IBinder {
        return super.peekService(myContext, service)
    }
}

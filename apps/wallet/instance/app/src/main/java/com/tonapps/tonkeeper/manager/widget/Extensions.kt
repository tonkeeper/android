package com.tonapps.tonkeeper.manager.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.tonapps.tonkeeper.core.widget.balance.WidgetBalanceProvider

fun WidgetManager.updateBalanceWidgets(context: Context, walletId: String) {
    val ids = getWidgets(context, TYPE_BALANCE, walletId).map { it.id }.toTypedArray()
    if (ids.isNotEmpty()) {
        val intent = Intent(context, WidgetBalanceProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}

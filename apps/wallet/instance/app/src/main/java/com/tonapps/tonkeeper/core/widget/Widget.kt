package com.tonapps.tonkeeper.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.widget.balance.WidgetBalanceProvider
import com.tonapps.tonkeeper.core.widget.rate.WidgetRateProvider
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

abstract class Widget: AppWidgetProvider() {

    companion object {

        fun updateAll() {
            WidgetBalanceProvider.requestUpdate()
            WidgetRateProvider.requestUpdate()
        }

        private val defaultIntent: Intent by lazy {
            val intent = Intent(App.instance, RootActivity::class.java)
            intent
        }

        val defaultPendingIntent: PendingIntent by lazy {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            PendingIntent.getActivity(App.instance, 0, defaultIntent, flags)
        }

        fun update(
            context: Context = App.instance,
            cls: Class<*>
        ) {
            val ids = getWidgetIds(context, cls)
            val intent = Intent(context, cls)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        private fun getWidgetIds(
            context: Context,
            cls: Class<*>
        ): IntArray {
            val provider = ComponentName(context, cls)
            return AppWidgetManager.getInstance(context).getAppWidgetIds(provider)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val scope = GlobalScope

    abstract fun update(context: Context, manager: AppWidgetManager, id: Int)

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
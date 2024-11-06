package com.tonapps.tonkeeper.manager.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

abstract class WidgetReceiver: AppWidgetProvider() {

    class Balance: WidgetReceiver()
    class Rate: WidgetReceiver()

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        WidgetManager.onUpdateInstalledWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
        WidgetManager.onUpdateInstalledWidgets(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        super.onRestored(context, oldIds, newIds)
    }

    override fun peekService(myContext: Context, service: Intent): IBinder {
        return super.peekService(myContext, service)
    }
}
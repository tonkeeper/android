package com.tonapps.tonkeeper.manager.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

abstract class WidgetReceiver: AppWidgetProvider() {

    class Balance: WidgetReceiver()
    class Rate: WidgetReceiver()

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        WidgetManager.onUpdateInstalledWidgets(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
        WidgetManager.onUpdateInstalledWidgets(context)
    }

}
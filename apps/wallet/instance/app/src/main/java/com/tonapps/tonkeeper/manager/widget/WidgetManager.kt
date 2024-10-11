package com.tonapps.tonkeeper.manager.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.whileTimeoutOrNull
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import kotlin.time.Duration.Companion.seconds

object WidgetManager {

    class PinnedReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra(ARG_TYPE) ?: return
            val extras = intent.extras ?: return
            if (type == TYPE_BALANCE) {
                pinnedBalance(context, extras)
            } else if (type == TYPE_RATE) {
                pinnedRate(context, extras)
            }
        }

        private fun pinnedBalance(context: Context, args: Bundle) {
            val params = args.getParcelableCompat<WidgetParams.Balance>(ARG_PARAMS) ?: return
            val widgetId = getWidgetIds(context).lastOrNull() ?: return
            settings.setType(widgetId, TYPE_BALANCE)
            settings.setParams(widgetId, params)

        }

        private fun pinnedRate(context: Context, args: Bundle) {
            val params = args.getParcelableCompat<WidgetParams.Rate>(ARG_PARAMS) ?: return
            val widgetId = getWidgetIds(context).lastOrNull() ?: return
            settings.setType(widgetId, TYPE_RATE)
            settings.setParams(widgetId, params)
        }
    }

    const val TYPE_RATE = "rate"
    const val TYPE_BALANCE = "balance"

    private const val ARG_PARAMS = "params"
    private const val ARG_TYPE = "type"

    private val settings: WidgetSettings by lazy {
        WidgetSettings(App.instance)
    }

    private val appWidgetManager: AppWidgetManager by lazy {
        AppWidgetManager.getInstance(App.instance)
    }

    val isRequestPinAppWidgetSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported

    fun installBalance(activity: Activity, walletId: String, jettonAddress: String? = null) {
        val params = WidgetParams.Balance(walletId, jettonAddress)
        install(activity, WidgetReceiver.Balance::class.java, TYPE_BALANCE, params)
    }

    fun installRate(activity: Activity, walletId: String, jettonAddress: String) {
        val params = WidgetParams.Rate(walletId, jettonAddress)
        install(activity, WidgetReceiver.Rate::class.java, TYPE_RATE, params)
    }

    suspend fun getBalanceParams(widgetId: Int): WidgetParams.Balance? {
        return whileTimeoutOrNull(5.seconds) {
            settings.getParams<WidgetParams.Balance>(widgetId)
        }
    }

    suspend fun getRateParams(widgetId: Int): WidgetParams.Rate? {
        return whileTimeoutOrNull(5.seconds) {
            settings.getParams<WidgetParams.Rate>(widgetId)
        }
    }

    private fun install(activity: Activity, cls: Class<*>, type: String, params: WidgetParams) {
        if (!isRequestPinAppWidgetSupported) {
            return
        }

        val intent = Intent(activity, PinnedReceiver::class.java).apply {
            putExtra(ARG_PARAMS, params)
            putExtra(ARG_TYPE, type)
        }

        val successCallback = PendingIntent.getBroadcast(
            activity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val provider = ComponentName(activity, cls)
        appWidgetManager.requestPinAppWidget(provider, null, successCallback)
    }

    private fun getWidgetIds(context: Context, cls: Class<*>): IntArray {
        val provider = ComponentName(context, cls)
        return AppWidgetManager.getInstance(context).getAppWidgetIds(provider).sortedArray()
    }

    private fun getWidgetIds(context: Context): IntArray {
        val balanceIds = getWidgetIds(context, WidgetReceiver.Balance::class.java)
        val rateIds = getWidgetIds(context, WidgetReceiver.Rate::class.java)
        return (balanceIds + rateIds).sortedArray()
    }

    suspend fun getWidgets(context: Context): List<WidgetEntity> {
        val ids = getWidgetIds(context)
        val list = mutableListOf<WidgetEntity>()
        for (id in ids) {
            val type = settings.getType(id) ?: continue

            if (type == TYPE_RATE) {
                val params = getRateParams(id) ?: continue
                list.add(WidgetEntity(
                    id = id,
                    params = params,
                    type = TYPE_RATE
                ))
                continue
            } else if (type == TYPE_BALANCE) {
                val params = getBalanceParams(id) ?: continue
                list.add(WidgetEntity(
                    id = id,
                    params = params,
                    type = TYPE_BALANCE
                ))
            }
        }
        return list.toList()
    }

    fun onUpdateInstalledWidgets(context: Context) {
        val widgetIds = getWidgetIds(context)
        if (widgetIds.isEmpty()) {
            WidgetUpdaterWorker.stop(context)
        } else {
            WidgetUpdaterWorker.start(context)
        }
    }

    fun hasWidgets(context: Context): Boolean {
        return getWidgetIds(context).isNotEmpty()
    }

    /*fun update(context: Context, cls: Class<*>) {
        val ids = getWidgetIds(context, cls)
        val intent = Intent(context, cls)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }*/

}
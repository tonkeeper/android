package com.tonapps.tonkeeper.manager.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.whileTimeoutOrNull
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.core.widget.balance.WidgetBalanceProvider
import com.tonapps.tonkeeper.core.widget.rate.WidgetRateProvider
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
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
            val params = args.getParcelableCompat<Widget.Params.Balance>(ARG_PARAMS) ?: return
            val widgetId = getWidgetIds(context, WidgetBalanceProvider::class.java).lastOrNull() ?: return
            settings.setParams(widgetId, params)
        }

        private fun pinnedRate(context: Context, args: Bundle) {
            val params = args.getParcelableCompat<Widget.Params.Rate>(ARG_PARAMS) ?: return
            val widgetId = getWidgetIds(context, WidgetRateProvider::class.java).lastOrNull() ?: return
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
        val params = Widget.Params.Balance(walletId, jettonAddress)
        install(activity, WidgetBalanceProvider::class.java, TYPE_BALANCE, params)
    }

    fun installRate(activity: Activity, walletId: String, jettonAddress: String) {
        val params = Widget.Params.Rate(walletId, jettonAddress)
        install(activity, WidgetRateProvider::class.java, TYPE_RATE, params)
    }

    suspend fun getBalanceParams(widgetId: Int): Widget.Params.Balance? {
        return whileTimeoutOrNull(5.seconds) {
            settings.getParams<Widget.Params.Balance>(widgetId)
        }
    }

    suspend fun getRateParams(widgetId: Int): Widget.Params.Rate? {
        return whileTimeoutOrNull(5.seconds) {
            settings.getParams<Widget.Params.Rate>(widgetId)
        }
    }

    private fun install(activity: Activity, cls: Class<*>, type: String, params: Widget.Params) {
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

    fun update(context: Context, cls: Class<*>) {
        val ids = getWidgetIds(context, cls)
        val intent = Intent(context, cls)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    fun getWidgets(context: Context, type: String): List<WidgetEntity> {
        val ids = getWidgetIds(context, when (type) {
            TYPE_BALANCE -> WidgetBalanceProvider::class.java
            TYPE_RATE -> WidgetRateProvider::class.java
            else -> return emptyList()
        })
        val list = mutableListOf<WidgetEntity>()
        for (id in ids) {
            val params = settings.getParams<Widget.Params.Balance>(id) ?: continue
            list.add(WidgetEntity(id, params, type))
        }
        return list.toList()
    }

    fun getWidgets(context: Context, type: String, walletId: String): List<WidgetEntity> {
        return getWidgets(context, type).filter { it.params.walletId == walletId }
    }

}
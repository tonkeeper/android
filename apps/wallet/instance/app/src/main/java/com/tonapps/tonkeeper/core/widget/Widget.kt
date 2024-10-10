package com.tonapps.tonkeeper.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import com.tonapps.extensions.string
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.widget.balance.WidgetBalanceProvider
import com.tonapps.tonkeeper.core.widget.rate.WidgetRateProvider
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

abstract class Widget<T: Widget.Params>: AppWidgetProvider() {

    abstract class Params(open val walletId: String): Parcelable {

        private companion object {
            private const val KEY_WALLET_ID = "wallet_id"
            private const val KEY_JETTON_ADDRESS = "jetton_address"

            private fun key(prefix: String, key: String) = "${prefix}.${key}"
        }

        open val isEmpty: Boolean
            get() = walletId.isEmpty()

        abstract fun save(prefix: String, prefs: SharedPreferences)

        @Parcelize
        data class Rate(
            override val walletId: String,
            val jettonAddress: String
        ): Params(walletId) {

            constructor(prefix: String, prefs: SharedPreferences) : this(
                walletId = prefs.getString(key(prefix, KEY_WALLET_ID), "")!!,
                jettonAddress = prefs.string(key(prefix, KEY_JETTON_ADDRESS)) ?: "TON"
            )

            override fun save(prefix: String, prefs: SharedPreferences) {
                prefs.edit()
                    .putString(key(prefix, KEY_WALLET_ID), walletId)
                    .putString(key(prefix, KEY_JETTON_ADDRESS), jettonAddress)
                    .apply()
            }

        }

        @Parcelize
        data class Balance(
            override val walletId: String,
            val jettonAddress: String? = null
        ): Params(walletId) {

            constructor(prefix: String, prefs: SharedPreferences) : this(
                walletId = prefs.getString(key(prefix, KEY_WALLET_ID), "")!!,
                jettonAddress = prefs.getString(key(prefix, KEY_JETTON_ADDRESS), null)
            )

            override fun save(prefix: String, prefs: SharedPreferences) {
                prefs.edit()
                    .putString(key(prefix, KEY_WALLET_ID), walletId)
                    .putString(key(prefix, KEY_JETTON_ADDRESS), jettonAddress)
                    .apply()
            }
        }
    }

    abstract class Content: Parcelable {

        @Parcelize
        data class Balance(
            val fiatBalance: CharSequence,
            val walletAddress: String,
            val label: CharSequence?,
            val color: Int,
        ): Content() {

            @IgnoredOnParcel
            val shortAddress = walletAddress.shortAddress

            val name: String
                get() = label?.toString() ?: shortAddress
        }


        @Parcelize
        data class Rate(
            val tokenName: String,
            val tokenPrice: CharSequence,
            val tokenIcon: Bitmap,
            val diff24h: String,
            val updatedDate: String
        ): Content()
    }

    companion object {

        private val defaultIntent: Intent by lazy {
            val intent = Intent(App.instance, RootActivity::class.java)
            intent
        }

        val defaultPendingIntent: PendingIntent by lazy {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            PendingIntent.getActivity(App.instance, 0, defaultIntent, flags)
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
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val extras = intent.extras?.keySet()?.joinToString(", ")
        }
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        super.onRestored(context, oldIds, newIds)
    }

    override fun peekService(myContext: Context, service: Intent): IBinder {
        return super.peekService(myContext, service)
    }
}
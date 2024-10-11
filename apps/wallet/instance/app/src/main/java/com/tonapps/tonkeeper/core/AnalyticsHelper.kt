package com.tonapps.tonkeeper.core

import android.content.Context
import androidx.annotation.UiThread
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.entity.ConfigEntity

object AnalyticsHelper {

    fun setConfig(context: Context, config: ConfigEntity) {
        initAptabase(context, config.aptabaseAppKey, config.aptabaseEndpoint)
    }

    @UiThread
    fun trackEvent(name: String) {
        Aptabase.instance.trackEvent(name)
    }

    @UiThread
    fun trackEventClickDApp(url: String) {
        Aptabase.instance.trackEvent("click_dapp", hashMapOf(url to "url"))
    }

    private fun initAptabase(
        context: Context,
        appKey: String,
        host: String
    ) {
        try {
            val options = InitOptions(
                host = host
            )
            Aptabase.instance.initialize(context, appKey, options)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
package com.tonkeeper

import android.content.Context
import com.tonkeeper.extensions.getEnum
import com.tonkeeper.extensions.putEnum
import ton.SupportedCurrency

class SettingsManager(context: Context) {

    private companion object {
        private const val CURRENCY_KEY = "currency"
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currency: SupportedCurrency
        get() = prefs.getEnum(CURRENCY_KEY, SupportedCurrency.USD)
        set(value) = prefs.edit().putEnum(CURRENCY_KEY, value).apply()
}
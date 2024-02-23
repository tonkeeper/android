package com.tonapps.wallet.data.settings

import android.content.Context
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.locale
import com.tonapps.extensions.putEnum
import com.tonapps.wallet.data.core.Currency

class SettingsRepository(
    private val context: Context
) {

    private companion object {
        private const val NAME = "settings"
        private const val CURRENCY_CODE_KEY = "currency_code"
        private const val LOCK_SCREEN_KEY = "lock_screen"
        private const val BIOMETRIC_KEY = "biometric"
        private const val COUNTRY_KEY = "country"
        private const val LANGUAGE_CODE_KEY = "language_code"
        private const val THEME_KEY = "theme"
    }

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: String = prefs.getString(THEME_KEY, "blue")!!
        set(value) {
            if (value != field) {
                prefs.edit().putString(THEME_KEY, value).apply()
                field = value
            }
        }

    var currency: Currency = Currency(prefs.getString(CURRENCY_CODE_KEY, Currency.FIAT.first())!!)
        set(value) {
            if (field != value) {
                prefs.edit().putString(CURRENCY_CODE_KEY, value.code).apply()
                field = value
            }
        }

    var language: String = prefs.getString(LANGUAGE_CODE_KEY, "en")!!
        set(value) {
            if (value != field) {
                field = value.ifEmpty {
                    "en"
                }
                prefs.edit().putString(LANGUAGE_CODE_KEY, field).apply()
            }
        }

    var lockScreen: Boolean = prefs.getBoolean(LOCK_SCREEN_KEY, true)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(LOCK_SCREEN_KEY, value).apply()
                field = value
            }
        }

    var biometric: Boolean = prefs.getBoolean(BIOMETRIC_KEY, false)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(BIOMETRIC_KEY, value).apply()
                field = value
            }
        }

    var country: String = prefs.getString(COUNTRY_KEY, null) ?: context.locale.country
        set(value) {
            if (value != field) {
                prefs.edit().putString(COUNTRY_KEY, value).apply()
                field = value
            }
        }
}
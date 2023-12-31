package com.tonkeeper

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.tonkeeper.core.language.AppLanguage
import com.tonkeeper.core.language.LANGUAGE_DEFAULT
import com.tonkeeper.extensions.getEnum
import com.tonkeeper.extensions.putEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import uikit.extensions.locale

class AppSettings(context: Context) {

    companion object {

        private const val NAME = "settings"
        private const val CURRENCY_KEY = "currency"
        private const val LOCK_SCREEN_KEY = "lock_screen"
        private const val BIOMETRIC_KEY = "biometric"
        private const val COUNTRY_KEY = "country"
        private const val LANGUAGE_KEY = "language"
    }

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var currency: SupportedCurrency = prefs.getEnum(CURRENCY_KEY, SupportedCurrency.USD)
        set(value) {
            if (field != value) {
                prefs.edit().putEnum(CURRENCY_KEY, value).apply()
                field = value
            }
        }

    var language: AppLanguage = prefs.getString(LANGUAGE_KEY, LANGUAGE_DEFAULT)!!
        set(value) {
            if (value != field) {
                field = value.ifEmpty {
                    LANGUAGE_DEFAULT
                }
                prefs.edit().putString(LANGUAGE_KEY, field).apply()
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

    var country: String = prefs.getString(COUNTRY_KEY, null) ?: App.instance.locale.country
        set(value) {
            if (value != field) {
                prefs.edit().putString(COUNTRY_KEY, value).apply()
                field = value
            }
        }
}


package com.tonapps.wallet.data.settings

import android.content.Context
import com.tonapps.extensions.locale
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.localization.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currencyFlow = MutableStateFlow<WalletCurrency?>(null)
    val currencyFlow = _currencyFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _languageFlow = MutableStateFlow<Language?>(null)
    val languageFlow = _languageFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: String = prefs.getString(THEME_KEY, "blue")!!
        set(value) {
            if (value != field) {
                prefs.edit().putString(THEME_KEY, value).apply()
                field = value
            }
        }

    var currency: WalletCurrency = WalletCurrency(prefs.getString(CURRENCY_CODE_KEY, WalletCurrency.FIAT.first())!!)
        set(value) {
            if (field != value) {
                prefs.edit().putString(CURRENCY_CODE_KEY, value.code).apply()
                field = value
                _currencyFlow.value = value
            }
        }

    var language: Language = Language(prefs.getString(LANGUAGE_CODE_KEY, "en") ?: Language.DEFAULT)
        set(value) {
            if (value != field) {
                field = value
                prefs.edit().putString(LANGUAGE_CODE_KEY, field.code).apply()
                _languageFlow.value = field
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

    init {
        scope.launch(Dispatchers.IO) {
            _currencyFlow.value = currency
            _languageFlow.value = language
        }
    }
}
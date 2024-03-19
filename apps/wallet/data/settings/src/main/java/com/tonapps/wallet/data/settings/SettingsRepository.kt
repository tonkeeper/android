package com.tonapps.wallet.data.settings

import android.content.Context
import com.tonapps.extensions.locale
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.localization.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
        private const val HIDDEN_BALANCES_KEY = "hidden_balances"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currencyFlow = MutableSharedFlow<WalletCurrency>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val currencyFlow = _currencyFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _languageFlow = MutableSharedFlow<Language>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val languageFlow = _languageFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _themeFlow = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val themeFlow = _themeFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _hiddenBalancesFlow = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hiddenBalancesFlow = _hiddenBalancesFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: String = prefs.getString(THEME_KEY, "blue")!!
        set(value) {
            if (value != field) {
                prefs.edit().putString(THEME_KEY, value).apply()
                field = value
                _themeFlow.tryEmit(value)
            }
        }

    var currency: WalletCurrency = WalletCurrency(prefs.getString(CURRENCY_CODE_KEY, WalletCurrency.FIAT.first())!!)
        set(value) {
            if (field != value) {
                prefs.edit().putString(CURRENCY_CODE_KEY, value.code).apply()
                field = value
                _currencyFlow.tryEmit(value)
            }
        }

    var language: Language = Language(prefs.getString(LANGUAGE_CODE_KEY, "en") ?: Language.DEFAULT)
        set(value) {
            if (value != field) {
                field = value
                prefs.edit().putString(LANGUAGE_CODE_KEY, field.code).apply()
                _languageFlow.tryEmit(field)
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

    var hiddenBalances: Boolean = prefs.getBoolean(HIDDEN_BALANCES_KEY, false)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(HIDDEN_BALANCES_KEY, value).apply()
                field = value
                _hiddenBalancesFlow.tryEmit(value)
            }
        }

    init {
        scope.launch(Dispatchers.IO) {
            _themeFlow.tryEmit(theme)
            _languageFlow.tryEmit(language)
            _currencyFlow.tryEmit(currency)
            _hiddenBalancesFlow.tryEmit(hiddenBalances)
        }
    }
}
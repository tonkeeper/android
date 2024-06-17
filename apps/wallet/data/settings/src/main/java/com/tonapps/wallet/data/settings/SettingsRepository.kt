package com.tonapps.wallet.data.settings

import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.content.edit
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.isMainVersion
import com.tonapps.extensions.locale
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.settings.folder.ImportLegacyFolder
import com.tonapps.wallet.data.settings.folder.TokenPrefsFolder
import com.tonapps.wallet.data.settings.folder.WalletPrefsFolder
import com.tonapps.wallet.localization.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// TODO need to be refactored
class SettingsRepository(
    private val scope: CoroutineScope,
    private val context: Context,
    private val rnLegacy: RNLegacy,
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
        private const val FIREBASE_TOKEN_KEY = "firebase_token"
        private const val INSTALL_ID_KEY = "install_id"
        private const val SEARCH_ENGINE_KEY = "search_engine"
        private const val AMOUNT_INPUT_CURRENCY_KEY = "amount_input_currency"
    }

    private val _currencyFlow = MutableEffectFlow<WalletCurrency>()
    val currencyFlow = _currencyFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _languageFlow = MutableEffectFlow<Language>()
    val languageFlow = _languageFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _themeFlow = MutableEffectFlow<Theme>()
    val themeFlow = _themeFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _hiddenBalancesFlow = MutableEffectFlow<Boolean>()
    val hiddenBalancesFlow = _hiddenBalancesFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _firebaseTokenFlow = MutableEffectFlow<String?>()
    val firebaseTokenFlow = _firebaseTokenFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _countryFlow = MutableEffectFlow<String>()
    val countryFlow = _countryFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _searchEngineFlow = MutableEffectFlow<SearchEngine>()
    val searchEngineFlow = _searchEngineFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _walletPush = MutableStateFlow<Map<String, Boolean>?>(null)
    val walletPush = _walletPush.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _amountInputCurrencyFlow = MutableStateFlow<Boolean?>(null)
    val amountInputCurrencyFlow = _amountInputCurrencyFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val tokenPrefsFolder = TokenPrefsFolder(context)
    private val walletPrefsFolder = WalletPrefsFolder(context)
    private val importLegacyFolder = ImportLegacyFolder(context)

    val tokenPrefsChangedFlow: Flow<Unit>
        get() = tokenPrefsFolder.changedFlow

    val installId: String
        get() = prefs.getString(INSTALL_ID_KEY, null) ?: run {
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(INSTALL_ID_KEY, id).apply()
            id
        }

    var amountInputCurrency: Boolean = prefs.getBoolean(AMOUNT_INPUT_CURRENCY_KEY, false)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(AMOUNT_INPUT_CURRENCY_KEY, value).apply()
                field = value
                _amountInputCurrencyFlow.tryEmit(value)
            }
        }

    var searchEngine: SearchEngine = SearchEngine(prefs.getString(SEARCH_ENGINE_KEY, "Google")!!)
        set(value) {
            if (value != field) {
                prefs.edit().putString(SEARCH_ENGINE_KEY, value.title).apply()
                field = value
                _searchEngineFlow.tryEmit(value)
            }
        }

    var theme: Theme = Theme.getByKey(prefs.getString(THEME_KEY, "blue")!!)
        set(value) {
            if (value != field) {
                prefs.edit().putString(THEME_KEY, value.key).apply()
                field = value
                _themeFlow.tryEmit(value)
            }
        }

    var firebaseToken: String? = prefs.getString(FIREBASE_TOKEN_KEY, null)
        set(value) {
            if (value != field) {
                prefs.edit().putString(FIREBASE_TOKEN_KEY, value).apply()
                field = value
                _firebaseTokenFlow.tryEmit(value)
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

    var lockScreen: Boolean = prefs.getBoolean(LOCK_SCREEN_KEY, false)
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
                _countryFlow.tryEmit(value)
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

    var importLegacyPasscode: Boolean
        get() = importLegacyFolder.passcode
        set(value) {
            importLegacyFolder.passcode = value
        }

    fun getPushWallet(walletId: String): Boolean = walletPrefsFolder.isPushEnabled(walletId)

    fun setPushWallet(walletId: String, value: Boolean) {
        walletPrefsFolder.setPushEnabled(walletId, value)

        val map = (_walletPush.value ?: mapOf()).toMutableMap()
        map[walletId] = value
        _walletPush.tryEmit(map)
    }

    fun setTokenHidden(walletId: String, tokenAddress: String, hidden: Boolean) {
        tokenPrefsFolder.setHidden(walletId, tokenAddress, hidden)
    }

    fun setTokenPinned(walletId: String, tokenAddress: String, pinned: Boolean) {
        tokenPrefsFolder.setPinned(walletId, tokenAddress, pinned)
    }

    fun setTokensSort(walletId: String, tokensAddress: List<String>) {
        tokenPrefsFolder.setSort(walletId, tokensAddress)
    }

    fun setWalletsSort(walletIds: List<String>) {
        walletPrefsFolder.setSort(walletIds)
    }

    fun getWalletPrefs(walletId: String) = walletPrefsFolder.get(walletId)

    fun getTokenPrefs(walletId: String, tokenAddress: String): TokenPrefsEntity {
        return tokenPrefsFolder.get(walletId, tokenAddress)
    }

    init {
        scope.launch(Dispatchers.IO) {
            if (context.isMainVersion && !importLegacyFolder.settings) {
                importFromLegacy()
                importLegacyFolder.settings = true
            }

            _currencyFlow.tryEmit(currency)
            _themeFlow.tryEmit(theme)
            _languageFlow.tryEmit(language)
            _hiddenBalancesFlow.tryEmit(hiddenBalances)
            _firebaseTokenFlow.tryEmit(firebaseToken)
            _countryFlow.tryEmit(country)
            _searchEngineFlow.tryEmit(searchEngine)
            _walletPush.tryEmit(mapOf())
            _amountInputCurrencyFlow.tryEmit(amountInputCurrency)
        }
    }

    private suspend fun importFromLegacy() {
        currency = legacyCurrency()
        language = legacyLanguage()
        searchEngine = legacySearchEngine()
        theme = legacyTheme()
        hiddenBalances = rnLegacy.getJSONState("privacy")?.getBoolean("hiddenAmounts") ?: false
        importLegacyWallets()
    }

    private suspend fun importLegacyWallets() {
        val data = rnLegacy.getWallets()
        lockScreen = data.lockScreenEnabled
        biometric = data.biometryEnabled

        val wallets = data.wallets
        for (wallet in wallets) {
            val key = "${wallet.identifier}/notifications"
            val isSubscribed = rnLegacy.getJSONValue(key)?.getBoolean("isSubscribed") ?: false
            walletPrefsFolder.setPushEnabled(wallet.identifier, isSubscribed)
        }
    }

    private fun legacyCurrency(): WalletCurrency {
        try {
            val value = rnLegacy.getJSONValue("ton_price")?.getString("currency") ?: "USD"
            return WalletCurrency(value.uppercase())
        } catch (e: Exception) {
            return WalletCurrency(WalletCurrency.FIAT.first())
        }
    }

    private fun legacyLanguage(): Language {
        try {
            val value = rnLegacy.getJSONState("in-app-language")?.getString("selectedLanguage")?.lowercase() ?: "system"
            return when (value) {
                "ru" -> Language("ru")
                "en" -> Language("en")
                else -> Language()
            }
        } catch (ignored: Exception) {}
        return Language()
    }

    private fun legacySearchEngine(): SearchEngine {
        try {
            val searchEngine = (rnLegacy.getJSONState("browser")?.getString("searchEngine") ?: "DuckDuckGo").lowercase()
            if (searchEngine == "google") {
                return SearchEngine.GOOGLE
            }
        } catch (ignored: Exception) {}
        return SearchEngine.DUCKDUCKGO
    }

    private fun legacyTheme(): Theme {
        try {
            val theme = rnLegacy.getJSONState("app-theme")?.getString("selectedTheme") ?: "blue"
            if (theme == "system") {
                return Theme.getByKey("blue")
            }
            return Theme.getByKey(theme)
        } catch (ignored: Exception) {}
        return Theme.getByKey("blue")
    }
}
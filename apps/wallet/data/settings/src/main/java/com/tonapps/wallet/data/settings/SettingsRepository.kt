package com.tonapps.wallet.data.settings

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.ArrayMap
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.isMainVersion
import com.tonapps.extensions.locale
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.core.isAvailableBiometric
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.entities.NftPrefsEntity
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.settings.folder.ImportLegacyFolder
import com.tonapps.wallet.data.settings.folder.NftPrefsFolder
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        private const val TELEGRAM_CHANNEL_KEY = "telegram_channel"
    }

    private val _currencyFlow = MutableEffectFlow<WalletCurrency>()
    val currencyFlow = _currencyFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull().distinctUntilChanged()

    private val _languageFlow = MutableEffectFlow<Language>()
    val languageFlow = _languageFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _telegramChannelFlow = MutableStateFlow(true)
    val telegramChannelFlow = _telegramChannelFlow.stateIn(scope, SharingStarted.Eagerly, true)

    private val _themeFlow = MutableEffectFlow<Theme>()
    val themeFlow = _themeFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _hiddenBalancesFlow = MutableEffectFlow<Boolean>()
    val hiddenBalancesFlow = _hiddenBalancesFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _firebaseTokenFlow = MutableEffectFlow<String?>()
    val firebaseTokenFlow = _firebaseTokenFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _countryFlow = MutableEffectFlow<String>()
    val countryFlow = _countryFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _biometricFlow = MutableStateFlow<Boolean?>(null)
    val biometricFlow = _biometricFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _lockscreenFlow = MutableStateFlow<Boolean?>(null)
    val lockscreenFlow = _lockscreenFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _searchEngineFlow = MutableEffectFlow<SearchEngine>()
    val searchEngineFlow = _searchEngineFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _walletPush = MutableStateFlow<Map<String, Boolean>?>(null)
    val walletPush = _walletPush.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val tokenPrefsFolder = TokenPrefsFolder(context)
    private val walletPrefsFolder = WalletPrefsFolder(context)
    private val nftPrefsFolder = NftPrefsFolder(context)
    private val migrationHelper = RNMigrationHelper(scope, context, rnLegacy)

    val walletPrefsChangedFlow: Flow<Unit>
        get() = walletPrefsFolder.changedFlow

    val tokenPrefsChangedFlow: Flow<Unit>
        get() = tokenPrefsFolder.changedFlow

    val nftPrefsChangedFlow: Flow<Unit>
        get() = nftPrefsFolder.changedFlow

    val installId: String
        get() = prefs.getString(INSTALL_ID_KEY, null) ?: run {
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(INSTALL_ID_KEY, id).apply()
            id
        }

    var searchEngine: SearchEngine = SearchEngine(prefs.getString(SEARCH_ENGINE_KEY, "Google")!!)
        set(value) {
            if (value != field) {
                prefs.edit().putString(SEARCH_ENGINE_KEY, value.title).apply()
                field = value
                _searchEngineFlow.tryEmit(value)
                migrationHelper.setLegacySearchEngine(value)
            }
        }

    var theme: Theme = Theme.getByKey(prefs.getString(THEME_KEY, "blue")!!)
        set(value) {
            if (value != field) {
                prefs.edit().putString(THEME_KEY, value.key).apply()
                field = value
                _themeFlow.tryEmit(value)
                migrationHelper.setLegacyTheme(value)
            }
        }

    var telegramChannel: Boolean = prefs.getBoolean(TELEGRAM_CHANNEL_KEY, true)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(TELEGRAM_CHANNEL_KEY, value).apply()
                field = value
                _telegramChannelFlow.tryEmit(value)
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

    var currency: WalletCurrency = WalletCurrency.of(prefs.getString(CURRENCY_CODE_KEY, null))
        set(value) {
            if (field != value && value.code.isNotEmpty()) {
                prefs.edit().putString(CURRENCY_CODE_KEY, value.code).apply()
                field = value
                _currencyFlow.tryEmit(value)
                migrationHelper.setLegacyCurrency(value)
            }
        }

    var language: Language = Language(prefs.getString(LANGUAGE_CODE_KEY, "en") ?: Language.DEFAULT)
        set(value) {
            if (value != field) {
                field = value
                prefs.edit().putString(LANGUAGE_CODE_KEY, field.code).apply()
                _languageFlow.tryEmit(field)
                migrationHelper.setLegacyLanguage(value)
            }
        }

    var lockScreen: Boolean = prefs.getBoolean(LOCK_SCREEN_KEY, false)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(LOCK_SCREEN_KEY, value).apply()
                field = value
                _lockscreenFlow.tryEmit(value)
                migrationHelper.setLockScreenEnabled(value)
            }
        }

    var biometric: Boolean = if (isAvailableBiometric(context)) prefs.getBoolean(BIOMETRIC_KEY, false) else false
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(BIOMETRIC_KEY, value).apply()
                field = value
                _biometricFlow.tryEmit(value)
                migrationHelper.setBiometryEnabled(value)
            }
        }

    var country: String = prefs.getString(COUNTRY_KEY, null) ?: context.locale.country
        set(value) {
            if (value != field) {
                prefs.edit().putString(COUNTRY_KEY, value).apply()
                field = value
                _countryFlow.tryEmit(value)
                migrationHelper.setLegacySelectedCountry(value)
            }
        }

    var hiddenBalances: Boolean = prefs.getBoolean(HIDDEN_BALANCES_KEY, false)
        set(value) {
            if (value != field) {
                prefs.edit().putBoolean(HIDDEN_BALANCES_KEY, value).apply()
                field = value
                _hiddenBalancesFlow.tryEmit(value)
                migrationHelper.setHiddenBalance(value)
            }
        }

    fun isPurchaseOpenConfirm(walletId: String, id: String) = walletPrefsFolder.isPurchaseOpenConfirm(walletId, id)

    fun disablePurchaseOpenConfirm(walletId: String, id: String) = walletPrefsFolder.disablePurchaseOpenConfirm(walletId, id)

    fun getPushWallet(walletId: String): Boolean = walletPrefsFolder.isPushEnabled(walletId)

    fun setPushWallet(walletId: String, value: Boolean) {
        walletPrefsFolder.setPushEnabled(walletId, value)

        val map = (_walletPush.value ?: mapOf()).toMutableMap()
        map[walletId] = value
        _walletPush.tryEmit(map)
    }

    fun isSetupHidden(walletId: String): Boolean = walletPrefsFolder.isSetupHidden(walletId)

    fun setupHide(walletId: String) {
        walletPrefsFolder.setupHide(walletId)
    }

    suspend fun setTokenHidden(
        walletId: String,
        tokenAddress: String,
        hidden: Boolean
    ) = withContext(Dispatchers.IO) {
        tokenPrefsFolder.setHidden(walletId, tokenAddress, hidden)
        rnLegacy.setTokenHidden(walletId, tokenAddress, hidden)
    }

    suspend fun setNftHidden(
        walletId: String,
        nftAddress: String,
        hidden: Boolean = true
    ) = withContext(Dispatchers.IO) {
        nftPrefsFolder.setHidden(walletId, nftAddress, hidden)
    }

    suspend fun setNftTrust(
        walletId: String,
        nftAddress: String,
        trust: Boolean = true
    ) = withContext(Dispatchers.IO) {
        nftPrefsFolder.setTrust(walletId, nftAddress, trust)
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

    fun getWalletLastUpdated(walletId: String) = walletPrefsFolder.getLastUpdated(walletId)

    fun setWalletLastUpdated(walletId: String) {
        walletPrefsFolder.setLastUpdated(walletId)
    }

    suspend fun getTokenPrefs(
        walletId: String,
        tokenAddress: String,
        blacklist: Boolean,
    ): TokenPrefsEntity = withContext(Dispatchers.IO) {
        tokenPrefsFolder.get(walletId, tokenAddress, blacklist)
    }

    suspend fun getNftPrefs(
        walletId: String,
        nftAddress: String
    ): NftPrefsEntity = withContext(Dispatchers.IO) {
        nftPrefsFolder.get(walletId, nftAddress)
    }

    init {
        languageFlow.onEach {
            AppCompatDelegate.setApplicationLocales(getLocales())
        }.launchIn(scope)

        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMigration()) {
                val legacyValues = importFromLegacy()
                biometric = legacyValues.biometric
                lockScreen = legacyValues.lockScreen
                currency = if (legacyValues.currency.code.isBlank()) {
                    WalletCurrency.DEFAULT
                } else {
                    legacyValues.currency
                }
                theme = legacyValues.theme
                language = legacyValues.language
                hiddenBalances = legacyValues.hiddenBalances
                country = legacyValues.country
                searchEngine = legacyValues.searchEngine
            }

            _currencyFlow.tryEmit(currency)
            _themeFlow.tryEmit(theme)
            _languageFlow.tryEmit(language)
            _hiddenBalancesFlow.tryEmit(hiddenBalances)
            _firebaseTokenFlow.tryEmit(firebaseToken)
            _countryFlow.tryEmit(country)
            _searchEngineFlow.tryEmit(searchEngine)
            _biometricFlow.tryEmit(biometric)
            _lockscreenFlow.tryEmit(lockScreen)
            _walletPush.tryEmit(mapOf())
        }
    }

    private data class LegacyValues(
        val currency: WalletCurrency,
        val language: Language,
        val searchEngine: SearchEngine,
        val theme: Theme,
        val hiddenBalances: Boolean,
        val country: String,
        val lockScreen: Boolean,
        val biometric: Boolean,
    )

    private suspend fun importFromLegacy(): LegacyValues {
        importLegacyWallets()
        return LegacyValues(
            currency = migrationHelper.getLegacyCurrency(),
            language = migrationHelper.getLegacyLanguage(),
            searchEngine = migrationHelper.getLegacySearchEngine(),
            theme = migrationHelper.getLegacyTheme(),
            hiddenBalances = migrationHelper.getHiddenBalances(),
            country = migrationHelper.getLegacySelectedCountry(),
            lockScreen = migrationHelper.getLockScreenEnabled(),
            biometric = migrationHelper.getBiometryEnabled(),
        )
    }

    private suspend fun importLegacyWallets() {
        val data = rnLegacy.getWallets()

        val wallets = data.wallets
        for (wallet in wallets) {
            val key = "${wallet.identifier}/notifications"
            val isSubscribed = rnLegacy.getJSONValue(key)?.getBoolean("isSubscribed") ?: false
            walletPrefsFolder.setPushEnabled(wallet.identifier, isSubscribed)
        }
    }

    private fun getLocales(): LocaleListCompat {
        val code = language.code
        return if (code == Language.DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
    }

}
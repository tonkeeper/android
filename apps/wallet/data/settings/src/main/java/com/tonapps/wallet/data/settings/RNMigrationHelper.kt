package com.tonapps.wallet.data.settings

import android.content.Context
import com.tonapps.extensions.locale
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNWallets
import com.tonapps.wallet.localization.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class RNMigrationHelper(
    private val scope: CoroutineScope,
    private val context: Context,
    private val rnLegacy: RNLegacy
) {

    fun getPrivacy(): JSONObject {
        return rnLegacy.getJSONState("privacy") ?: JSONObject()
    }

    fun setPrivacy(json: JSONObject) {
        rnLegacy.setJSONState("privacy", json)
    }

    fun getHiddenBalances(): Boolean {
        return getPrivacy().optBoolean("hiddenAmounts", false)
    }

    fun setHiddenBalance(hiddenBalance: Boolean) {
        scope.launch(Dispatchers.IO) {
            val privacy = getPrivacy()
            privacy.put("hiddenAmounts", hiddenBalance)
            setPrivacy(privacy)
        }
    }

    fun getTonPrice(): JSONObject {
        return rnLegacy.getJSONValue("ton_price") ?: JSONObject()
    }

    fun setTonPrice(json: JSONObject) {
        rnLegacy.setJSONValue("ton_price", json)
    }

    fun getLegacyCurrency(): WalletCurrency {
        val value = getTonPrice().optString("currency") ?: WalletCurrency.FIAT.first()
        return WalletCurrency.ofOrDefault(value.uppercase())
    }

    fun setLegacyCurrency(currency: WalletCurrency) {
        scope.launch(Dispatchers.IO) {
            val tonPrice = getTonPrice()
            tonPrice.put("currency", currency.code)
            setTonPrice(tonPrice)
        }
    }

    fun getInAppLanguage(): JSONObject {
        return rnLegacy.getJSONState("in-app-language") ?: JSONObject()
    }

    fun setInAppLanguage(json: JSONObject) {
        rnLegacy.setJSONState("in-app-language", json)
    }

    fun getLegacyLanguage(): Language {
        val value = getInAppLanguage()

        val lang = value.optString("selectedLanguage", "").lowercase().ifBlank {
            "system"
        }

        return when (lang) {
            "ru" -> Language("ru")
            "en" -> Language("en")
            else -> Language()
        }
    }

    fun setLegacyLanguage(language: Language) {
        scope.launch(Dispatchers.IO) {
            val inAppLanguage = getInAppLanguage()
            inAppLanguage.put("selectedLanguage", language.code)
            setInAppLanguage(inAppLanguage)
        }
    }

    private fun getLegacyBrowser(): JSONObject {
        return rnLegacy.getJSONState("browser") ?: JSONObject()
    }

    private fun setLegacyBrowser(json: JSONObject) {
        rnLegacy.setJSONState("browser", json)
    }

    fun getLegacySearchEngine(): SearchEngine {
        try {
            val searchEngine = getLegacyBrowser().optString("searchEngine").lowercase().ifBlank {
                "DuckDuckGo"
            }
            if (searchEngine == "google") {
                return SearchEngine.GOOGLE
            }
        } catch (ignored: Exception) {}
        return SearchEngine.DUCKDUCKGO
    }

    fun setLegacySearchEngine(searchEngine: SearchEngine) {
        scope.launch(Dispatchers.IO) {
            val browser = getLegacyBrowser()
            if (searchEngine == SearchEngine.GOOGLE) {
                browser.put("searchEngine", "Google")
            } else {
                browser.put("searchEngine", "DuckDuckGo")
            }
            setLegacyBrowser(browser)
        }
    }

    private fun getLegacyAppTheme(): JSONObject {
        return rnLegacy.getJSONState("app-theme") ?: JSONObject()
    }

    fun setLegacyAppTheme(json: JSONObject) {
        rnLegacy.setJSONState("app-theme", json)
    }

    fun getLegacyTheme(): Theme {
        try {
            val value = getLegacyAppTheme()
            val theme = value.optString("selectedTheme").lowercase().ifBlank {
                "system"
            }
            if (theme == "system") {
                return Theme.getByKey("blue")
            }
            return Theme.getByKey(theme)
        } catch (ignored: Exception) { }
        return Theme.getByKey("blue")
    }

    fun setLegacyTheme(theme: Theme) {
        scope.launch(Dispatchers.IO) {
            val appTheme = getLegacyAppTheme()
            appTheme.put("selectedTheme", theme.key)
            setLegacyAppTheme(appTheme)
        }
    }

    fun getFiatMethods(): JSONObject {
        return rnLegacy.getJSONState("fiat-methods") ?: JSONObject()
    }

    fun setFiatMethods(json: JSONObject) {
        rnLegacy.setJSONState("fiat-methods", json)
    }

    fun getLegacySelectedCountry(): String {
        try {
            return getFiatMethods().optString("selectedCountry", "").ifBlank {
                context.locale.country
            }
        } catch (ignored: Exception) {}
        return context.locale.country
    }

    fun setLegacySelectedCountry(country: String) {
        scope.launch(Dispatchers.IO) {
            val fiatMethods = getFiatMethods()
            fiatMethods.put("selectedCountry", country)
            setFiatMethods(fiatMethods)
        }
    }

    fun setBiometryEnabled(enabled: Boolean) {
        scope.launch {
            val wallets = getWallets()
            setWallets(wallets.copy(biometryEnabled = enabled))
        }
    }

    fun setLockScreenEnabled(enabled: Boolean) {
        scope.launch {
            val wallets = getWallets()
            setWallets(wallets.copy(lockScreenEnabled = enabled))
        }
    }

    suspend fun getBiometryEnabled(): Boolean {
        return getWallets().biometryEnabled
    }

    suspend fun getLockScreenEnabled(): Boolean {
        return getWallets().lockScreenEnabled
    }

    suspend fun getWallets(): RNWallets {
        return rnLegacy.getWallets()
    }

    suspend fun setWallets(wallets: RNWallets) {
        rnLegacy.setWallets(wallets)
    }

}
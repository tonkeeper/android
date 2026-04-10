package com.tonapps.tonkeeper

import android.content.Context
import androidx.compose.runtime.Composable
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.os.AppInstall
import com.tonapps.tonkeeper.os.DeviceCountry
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import ui.theme.AppColorScheme
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight
import java.util.Locale

class Environment(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : EnvironmentHelper.Delegate {

    data class CountryData(
        val fromStore: String? = null,
        val bySimCard: String? = null,
        val byNetwork: String? = null,
        val byIPAddress: String? = null,
        val byLocale: String? = null,
        val timeZone: String? = null,
        val isUsingVpn: Boolean = false,
        val debug: String? = null,
    ) {

        private val debugValue: String?
            get() = if (BuildConfig.DEBUG) debug else null

        val value: String?
            get() = (debugValue ?: bySimCard ?: byNetwork ?: byIPAddress ?: byLocale)?.let(::fixCountryCode)

        val storeCountry: String?
            get() = (debugValue ?: fromStore)?.let(::fixCountryCode)
    }

    private val _countryDataFlow = MutableStateFlow<CountryData>(CountryData())
    val countryDataFlow = _countryDataFlow.asStateFlow()
    val countryFlow = _countryDataFlow.asStateFlow().mapNotNull { it.value }.distinctUntilChanged()

    val deviceCountry: String
        get() = _countryDataFlow.value.value ?: Locale.getDefault().country

    val storeCountry: String?
        get() = _countryDataFlow.value.storeCountry

    val simCountry: String?
        get() = _countryDataFlow.value.bySimCard

    val timezone: String?
        get() = _countryDataFlow.value.timeZone

    val vpnActive: Boolean
        get() = _countryDataFlow.value.isUsingVpn


    init {
        setDebugCountry(DevSettings.country)

        _countryDataFlow.value = _countryDataFlow.value.copy(
            bySimCard = DeviceCountry.fromSIM(context)?.uppercase(),
            byNetwork = DeviceCountry.fromNetwork(context)?.uppercase(),
            byLocale = DeviceCountry.fromLocale()?.uppercase(),
            timeZone = DeviceCountry.timeZone(),
            isUsingVpn = DeviceCountry.isUsingVpn(context)
        )
    }

    @get:Composable
    val theme: AppColorScheme
        get() {
            return when(settingsRepository.theme.key) {
                "blue" -> appColorSchemeBlue()
                "dark" -> appColorSchemeDark()
                "light" -> appColorSchemeLight()
                else -> if (context.isDarkMode) appColorSchemeBlue() else appColorSchemeLight()
            }
        }

    val installerSource: AppInstall.Source by lazy { AppInstall.request(context) }

    val isFromGooglePlay: Boolean by lazy {
        installerSource == AppInstall.Source.GOOGLE_PLAY || installerSource == AppInstall.Source.AURORA_STORE
    }

    val isGooglePlayServicesAvailable: Boolean by lazy {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        resultCode == ConnectionResult.SUCCESS
    }

    fun setDebugCountry(country: String?) {
        if (BuildConfig.DEBUG) {
            _countryDataFlow.value = _countryDataFlow.value.copy(debug = country?.uppercase())
        }
    }

    fun setCountryFromStore(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(fromStore = country?.uppercase())
    }

    fun setCountryByIPAddress(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(byIPAddress = country?.uppercase())
    }

    override fun deviceCountry(): String {
        return deviceCountry
    }

    override fun storeCountry(): String? {
        return storeCountry
    }

    override fun simCountry(): String? {
        return simCountry
    }

    override fun timezone(): String? {
        return timezone
    }

    override fun isVpnActive(): Boolean {
        return vpnActive
    }

    private companion object {
        private fun fixCountryCode(country: String): String {
            val fixedCountry = if (country.length == 2) country.uppercase() else "AE"
            if (BuildConfig.DEBUG) {
                return DevSettings.country ?: fixedCountry
            }
            return fixedCountry
        }
    }
}
package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale

fun SettingsRepository.getNormalizeCountryFlow(api: API) = countryFlow.map { country ->
    if (country.equals("AUTO", true) || country.isEmpty()) {
        api.resolveCountry() ?: getLocale().country
    } else {
        country
    }
}.flowOn(Dispatchers.IO)

fun SettingsRepository.getLocaleCountryFlow(api: API) = getNormalizeCountryFlow(api).map {
    Locale("", it)
}

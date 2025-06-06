package com.tonapps.tonkeeper.extensions

import android.util.Log
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

data class OnRampResult(
    val data: OnRamp.Data,
    val country: String
)

fun PurchaseRepository.providersFlow(
    testnet: Boolean,
    settingsRepository: SettingsRepository,
    api: API
): Flow<List<PurchaseMethodEntity>> {
    return settingsRepository.getNormalizeCountryFlow(api).mapNotNull { country ->
        get(testnet, country, settingsRepository.getLocale())
    }.map { (it.first + it.first) }.map { entities ->
        entities.flatMap { it.items }.distinctBy { it.title }
    }.flowOn(Dispatchers.IO)
}

fun PurchaseRepository.onRampDataFlow(
    settingsRepository: SettingsRepository,
    api: API
) = settingsRepository
    .getNormalizeCountryFlow(api)
    .mapNotNull { country ->
        getOnRamp(country)?.let { data ->
            OnRampResult(data, country)
        }
    }

suspend fun PurchaseRepository.getProvidersByCountry(
    wallet: WalletEntity,
    settingsRepository: SettingsRepository,
    country: String
): List<PurchaseMethodEntity> = withContext(Dispatchers.IO) {
    val methods = get(wallet.testnet, country, settingsRepository.getLocale()) ?: return@withContext emptyList()
    val all = methods.first + methods.second
    all.map { it.items }.flatten().distinctBy { it.title }
}

fun PurchaseRepository.sendCurrencyFlow(settingsRepository: SettingsRepository): Flow<WalletCurrency> {
    return sendCurrencyFlow.map {
        it ?: settingsRepository.currency
    }
}

fun PurchaseRepository.onRampFormCurrencyFlow(
    settingsRepository: SettingsRepository
): Flow<Pair<WalletCurrency, WalletCurrency>> {
    return combine(
        sendCurrencyFlow(settingsRepository),
        receiveCurrencyFlow
    ) { sendCurrency, receiveCurrency ->
        /*if (sendCurrency.fiat && receiveCurrency.fiat) {
            Pair(sendCurrency, WalletCurrency.TON)
        } else if (sendCurrency.isUSDT && receiveCurrency.isUSDT) {
            Pair(settingsRepository.currency, receiveCurrency)
        } else if (!sendCurrency.fiat && !receiveCurrency.fiat) {
            Pair(settingsRepository.currency, receiveCurrency)
        } else {
            Pair(sendCurrency, receiveCurrency)
        }*/
        Pair(sendCurrency, receiveCurrency)
    }
}

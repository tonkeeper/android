package com.tonapps.deposit.data

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
import io.exchangeapi.infrastructure.ApiResult
import io.exchangeapi.infrastructure.extractApiErrorMessage
import io.exchangeapi.models.CreateExchangeRequest
import io.exchangeapi.models.CreateP2PSessionRequest
import io.exchangeapi.models.ExchangeCalculateRequest
import io.exchangeapi.models.ExchangeCalculation
import io.exchangeapi.models.ExchangeFlow
import io.exchangeapi.models.ExchangeLayout
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.ExchangeResult
import io.exchangeapi.models.P2PSessionResult
import io.exchangeapi.models.Platform
import kotlinx.coroutines.withContext
import java.util.Locale

class ExchangeRepository(
    private val api: API,
    private val environment: EnvironmentHelper,
) {
    sealed interface Keys : CacheKey {
        data object LayoutOn : Keys
        data object LayoutOff : Keys
        data object Merchants : Keys
        data object Currencies : Keys
    }

    private val cache = TimedCacheMemory<Keys>()

    suspend fun clearRampCache(rampType: RampType) {
        cache.remove(
            key = when (rampType) {
                RampType.RampOff -> Keys.LayoutOff
                RampType.RampOn -> Keys.LayoutOn
            }
        )
    }

    suspend fun getLayout(rampType: RampType): ExchangeLayout {
        val key = when (rampType) {
            RampType.RampOn -> Keys.LayoutOn
            RampType.RampOff -> Keys.LayoutOff
        }
        return cache.getOrLoad(key) {
            withContext(Async.Io) {
                getLayoutCurrency(rampType, null)
            }
        }
    }


    suspend fun getLayoutCurrency(rampType: RampType, currency: WalletCurrency?): ExchangeLayout {
        return withContext(Async.Io) {
            api.exchange.exchange.getExchangeLayout(
                flow = when (rampType) {
                    RampType.RampOn -> ExchangeFlow.deposit
                    RampType.RampOff -> ExchangeFlow.withdraw
                },
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                currency = currency?.code
            )
        }
    }

    suspend fun getCurrencies(network: TonNetwork, locale: Locale): List<WalletCurrency> {
        return cache.getOrLoad(Keys.Currencies) {
            val result = api.getCurrencies(network, locale)
            buildList {
                for (i in 0 until result.length()) {
                    val item = result.getJSONObject(i)
                    val code = item.getString("code")
                    val type = item.getString("type")
                    if (type.equals("fiat", ignoreCase = true)) {
                        add(WalletCurrency.fiat(code))
                    }
                }
            }
                .let { WalletCurrency.sort(it) }
        }
    }

    suspend fun getMerchants(): List<ExchangeMerchantInfo> {
        return cache.getOrLoad(Keys.Merchants) {
            withContext(Async.Io) {
                api.exchange.exchange.getExchangeMerchants(
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                    platform = Platform.android,
                )
            }
        }
    }

    suspend fun calculate(request: ExchangeCalculateRequest): ExchangeCalculation {
        return withContext(Async.Io) {
            api.exchange.exchange.exchangeCalculate(
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                exchangeCalculateRequest = request,
                platform = Platform.android,
            )
        }
    }

    suspend fun createExchange(request: CreateExchangeRequest): ApiResult<ExchangeResult> {
        return withContext(Async.Io) {
            try {
                val result = api.exchange.exchange.createExchange(
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                    createExchangeRequest = request,
                    platform = Platform.android,
                )
                ApiResult.Success(result)
            } catch (e: Throwable) {
                ApiResult.Error(extractApiErrorMessage(e))
            }
        }
    }

    suspend fun createP2PSession(request: CreateP2PSessionRequest): P2PSessionResult {
        return withContext(Async.Io) {
            api.exchange.p2p.createP2PSession(
                createP2PSessionRequest = request,
            )
        }
    }
}

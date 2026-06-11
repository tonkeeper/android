package com.tonapps.trading.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint
import io.tradingapi.models.AssetDetailsResponse
import io.tradingapi.models.AssetsCatalogResponse
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.ShelfGroup
import kotlinx.coroutines.withContext

class TradingRepository(
    private val api: API,
    private val environment: EnvironmentHelper,
) {
    sealed interface Keys : CacheKey {
        data object Shelves : Keys
    }

    private val cache = TimedCacheMemory<Keys>()

    suspend fun getShelfGroups(): List<ShelfGroup> {
        return cache.getOrLoad(Keys.Shelves) {
            withContext(Async.Io) {
                api.trading.shelves.getShelvesConfig(
                    xLang = environment.locale(),
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                    currency = environment.currency(),
                ).groups.filter { it.items.isNotEmpty() }
            }
        }
    }

    suspend fun getAssets(
        query: String,
        tab: AssetsTab,
        cursor: String?
    ): AssetsCatalogResponse {
        return withContext(Async.Io) {
            api.trading.assets.getAssetsCatalog(
                q = query.ifBlank { null },
                tab = tab,
                cursor = cursor,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                currency = environment.currency(),
            )
        }
    }

    suspend fun getAssetDetails(assetId: String): AssetDetailsResponse {
        return withContext(Async.Io) {
            api.trading.assets.getAssetDetails(
                assetId = assetId,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                currency = environment.currency(),
            )
        }
    }

    suspend fun getAssetChart(chartToken: String, period: ChartPeriod): List<ChartPoint> {
        return withContext(Async.Io) {
            val endDate = System.currentTimeMillis() / 1000
            val startDate = endDate - period.durationSeconds
            api.loadChart(chartToken, environment.currency(), startDate, endDate)
                .map { ChartPoint(it.date, it.price) }
        }
    }

    suspend fun clearCache() {
        cache.remove(Keys.Shelves)
    }

    fun getCurrency(): String = environment.currency()
}

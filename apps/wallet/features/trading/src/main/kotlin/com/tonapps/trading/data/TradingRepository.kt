package com.tonapps.trading.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
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
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                ).groups.filter { it.items.isNotEmpty() }
            }
        }
    }

    suspend fun clearCache() {
        cache.remove(Keys.Shelves)
    }
}

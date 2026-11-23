package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAssetSource
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformAsset
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class MoonPayAssetRepository(
    private val platformApi: (MoonPayEnvironment) -> MoonPayPlatformApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val slots = ConcurrentHashMap<Key, Slot>()

    suspend fun find(connection: MoonPayConnection, assetId: String, symbol: String): Result<MoonPayPlatformAsset?> = catchingResult {
        val environment = connection.environment
        MoonPayAssetResolver.match(assetId, symbol, assets(connection, Key(environment, MoonPayAssetSource.Cefi, code = null)))
            ?: MoonPayAssetResolver.match(assetId, symbol, assets(connection, Key(environment, MoonPayAssetSource.Defi, symbol)))
    }

    private suspend fun assets(connection: MoonPayConnection, key: Key): List<MoonPayPlatformAsset> {
        val slot = slots.computeIfAbsent(key) { Slot() }
        return slot.mutex.withLock {
            val kept = slot.assets?.takeIf { slot.loadedAt?.let { it.elapsedNow() < CACHE_LIFETIME } == true }
            kept ?: load(connection, key).also {
                slot.assets = it
                slot.loadedAt = timeSource.markNow()
            }
        }
    }

    private suspend fun load(connection: MoonPayConnection, key: Key): List<MoonPayPlatformAsset> {
        val api = platformApi(connection.environment)
        val max = when (key.source) {
            MoonPayAssetSource.Cefi -> MAX_CEFI
            MoonPayAssetSource.Defi -> MAX_DEFI
        }
        val assets = mutableListOf<MoonPayPlatformAsset>()
        var cursor: String? = null
        do {
            val page = api.assets(connection.credentials.accessToken, key.source, key.code, cursor).getOrThrow()
            assets += page.assets
            cursor = page.nextCursor
        } while (cursor != null && assets.size < max)
        return assets
    }

    private data class Key(val environment: MoonPayEnvironment, val source: MoonPayAssetSource, val code: String?)

    private class Slot {
        val mutex = Mutex()
        var assets: List<MoonPayPlatformAsset>? = null
        var loadedAt: TimeMark? = null
    }

    private companion object {
        const val MAX_CEFI = 5000
        const val MAX_DEFI = 1000
        val CACHE_LIFETIME = 15.minutes
    }
}

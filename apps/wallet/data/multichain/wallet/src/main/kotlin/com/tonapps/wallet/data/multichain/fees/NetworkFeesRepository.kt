package com.tonapps.wallet.data.multichain.fees

import com.tonapps.async.Async
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.DateTimeProvider
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import io.batteryapi.models.NetworkFees as NetworkFeesResponse

/**
 * Battery's `/network-fees`, one response for the whole app (it is the same for every wallet), kept
 * for [TTL]. Concurrent loads wait for one request, and a failed load falls back to the last response.
 */
class NetworkFeesRepository(
    private val api: API,
    dateTimeProvider: DateTimeProvider = DateTimeProvider { Clock.System.now().toEpochMilliseconds() },
) {

    private data object Key : CacheKey {
        override val ttl: Long = TTL
    }

    private val cache = TimedCacheMemory<Key>(dateTimeProvider)

    /** The last response loaded, for readers that cannot wait for the network; null until the first one. */
    val fees: StateFlow<NetworkFees?> field = MutableStateFlow<NetworkFees?>(null)

    /** The response, from the cache while it is fresh; the last one, or null, when loading it fails. */
    suspend fun load(): NetworkFees? {
        return try {
            cache.getOrLoad(Key) { fetch() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fees.value
        }
    }

    private suspend fun fetch(): NetworkFees {
        val response = withContext(Async.Io) { api.getBatteryNetworkFees() }
            ?: throw IOException("network-fees: no response")

        val loaded = response.toNetworkFees()
        fees.value = loaded
        return loaded
    }

    companion object {
        val TTL = 5.minutes.inWholeMilliseconds
    }
}

private fun NetworkFeesResponse.toNetworkFees(): NetworkFees {
    // An amount that is not an integer drops its entry, so that asset keeps the behaviour it had without one.
    val entries = assets.mapNotNull { fee ->
        NetworkFees.Entry(
            assetId = fee.assetId,
            feeAssetId = fee.feeAssetId,
            swapSafe = fee.swap.safe.toBigIntegerOrNull() ?: return@mapNotNull null,
            perpsSafe = fee.perps?.let { it.safe.toBigIntegerOrNull() ?: return@mapNotNull null },
        )
    }
    return NetworkFees(entries)
}

private fun String.toBigIntegerOrNull(): BigInteger? {
    return toBigIntegerOrNull(radix = 10)?.takeIf { it.signum() >= 0 }
}

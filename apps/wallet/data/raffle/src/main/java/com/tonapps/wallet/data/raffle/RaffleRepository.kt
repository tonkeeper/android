package com.tonapps.wallet.data.raffle

import android.content.Context
import android.os.SystemClock
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.MarkWalletRaffleImportRequest
import io.walletapi.models.Raffle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.util.concurrent.ConcurrentHashMap

class RaffleRepository(
    context: Context,
    private val api: API,
    private val settingsRepository: SettingsRepository,
) {

    private val diskCache = BlobDataSource.simpleJSON<List<Raffle>>(context, "raffles")
    private val memoryCache = MutableStateFlow<Map<String, List<Raffle>>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetches = ConcurrentHashMap<String, Prefetch>()

    private val lang: String
        get() = settingsRepository.getLocale().language

    fun getRafflesFlow(walletId: String, forced: Boolean): Flow<List<RaffleEntity>> =
        remoteFlow(walletId, forced, reusePrefetch = false).map { raffles -> raffles.mapNotNull { it.toEntity() } }

    /** A forced [getRafflesFlow] that skips the request if a [prefetch] started less than 10 s ago succeeds. */
    fun getRafflesFlowAfterPrefetch(walletId: String): Flow<List<RaffleEntity>> =
        remoteFlow(walletId, forced = true, reusePrefetch = true).map { raffles -> raffles.mapNotNull { it.toEntity() } }

    suspend fun getRaffle(
        walletId: String,
        raffleId: String?,
        forced: Boolean,
    ): RaffleEntity? {
        val entities = getEntities(walletId, forced)
        return entities.firstOrNull { it.id == raffleId } ?: entities.firstOrNull { !it.hasLeaderboard }
    }

    suspend fun getRaffleWithStory(walletId: String, storyId: String): RaffleEntity? =
        getEntities(walletId, forced = false).firstOrNull { it.ownsStoryId(storyId) }

    private suspend fun getEntities(walletId: String, forced: Boolean): List<RaffleEntity> = withContext(Dispatchers.IO) {
        val key = cacheKey(walletId)
        val raffles = if (forced) {
            loadRemote(walletId) ?: cached(key)
        } else {
            cached(key) ?: loadRemote(walletId)
        }
        raffles?.mapNotNull { it.toEntity() }.orEmpty()
    }

    suspend fun prefetch(walletId: String) {
        val prefetch = Prefetch(SystemClock.elapsedRealtime())
        prefetches[cacheKey(walletId)] = prefetch
        try {
            // Inside the IO block: a cancelled prefetch still finishes its blocking request and writes the cache.
            withContext(Dispatchers.IO) { prefetch.succeeded.complete(loadRemote(walletId) != null) }
        } finally {
            prefetch.succeeded.complete(false)
        }
    }

    suspend fun reportImport(sourceWalletId: String, importedWalletId: String) = withContext(Dispatchers.IO) {
        try {
            api.multichain.raffles.markWalletRaffleImport(
                sourceWalletId,
                MarkWalletRaffleImportRequest(importedWalletId),
                xWalletId = sourceWalletId,
            )
            loadRemote(sourceWalletId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("RaffleRepository", "Failed to report raffle import", e)
        }
    }

    private fun remoteFlow(walletId: String, forced: Boolean, reusePrefetch: Boolean): Flow<List<Raffle>> = flow {
        val key = cacheKey(walletId)
        seedFromDisk(key)
        if (forced) {
            scope.launch {
                if (!reusePrefetch || !recentPrefetchSucceeded(key)) {
                    loadRemote(walletId)
                }
            }
        }
        emitAll(memoryCache.map { it[key] ?: emptyList() }.distinctUntilChanged())
    }.flowOn(Dispatchers.IO)

    private fun seedFromDisk(key: String) {
        if (memoryCache.value.containsKey(key)) {
            return
        }
        val disk = diskCache.getCache(key)?.distinctBy { it.id } ?: return
        memoryCache.update { current ->
            if (current.containsKey(key)) {
                current
            } else {
                current + (key to disk)
            }
        }
    }

    private suspend fun recentPrefetchSucceeded(key: String): Boolean {
        val prefetch = prefetches[key] ?: return false
        if (SystemClock.elapsedRealtime() - prefetch.startedAt >= RECENT_PREFETCH_MS) {
            return false
        }
        return prefetch.succeeded.await()
    }

    private fun cached(key: String): List<Raffle>? = memoryCache.value[key] ?: diskCache.getCache(key)

    private fun loadRemote(walletId: String): List<Raffle>? {
        val key = cacheKey(walletId)
        val isNew = !settingsRepository.hadWalletsOnMultichainRelease
        return try {
            val raffles = api.multichain.raffles
                .getWalletRaffles(walletId, lang, null, isNew)
                .raffles
                .distinctBy { it.id }
            diskCache.setCache(key, raffles)
            memoryCache.update { it + (key to raffles) }
            raffles
        } catch (e: SerializationException) {
            // A decode failure means the payload is shaped for a newer app version, so parsing
            // will keep failing until the app is updated. Drop the disk cache instead of leaving
            // it frozen forever on the last state we could still read.
            FirebaseCrashlytics.getInstance().recordException(e)
            invalidateCache(key)
            null
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    private fun invalidateCache(key: String) {
        diskCache.clearCache(key)
        memoryCache.update { it - key }
    }

    private fun cacheKey(walletId: String) = "raffles_${lang}_$walletId"

    private companion object {
        const val RECENT_PREFETCH_MS = 10_000L
    }
}

private class Prefetch(val startedAt: Long) {
    val succeeded = CompletableDeferred<Boolean>()
}

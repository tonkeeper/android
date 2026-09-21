package com.tonapps.wallet.data.raffle

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.raffle.debug.RaffleDebugStore
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.MarkWalletRaffleImportRequest
import io.walletapi.models.Raffle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

class RaffleRepository(
    context: Context,
    private val api: API,
    private val debugStore: RaffleDebugStore,
    private val settingsRepository: SettingsRepository,
) {

    private val diskCache = BlobDataSource.simpleJSON<List<Raffle>>(context, "raffles")
    private val memoryCache = MutableStateFlow<Map<String, List<Raffle>>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lang: String
        get() = settingsRepository.getLocale().language

    fun getRafflesFlow(walletId: String, forced: Boolean): Flow<List<RaffleEntity>> =
        when {
            WalletFeature.Raffles.isDisabled -> flowOf(emptyList())
            else -> remoteFlow(walletId, forced)
        }.map { raffles -> raffles.map { it.toEntity() } }

    suspend fun getRaffle(
        walletId: String,
        raffleId: String?,
        forced: Boolean,
    ): RaffleEntity? = withContext(Dispatchers.IO) {
        val key = cacheKey(walletId)
        val raffles = when {
            WalletFeature.Raffles.isDisabled -> null
            forced -> loadRemote(walletId) ?: cached(key)
            else -> cached(key) ?: loadRemote(walletId)
        }
        val raffle = raffles?.firstOrNull { it.id == raffleId } ?: raffles?.firstOrNull()
        raffle?.toEntity()
    }

    suspend fun prefetch(walletId: String) {
        if (WalletFeature.Raffles.isDisabled) {
            return
        }
        withContext(Dispatchers.IO) { loadRemote(walletId) }
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

    private fun remoteFlow(walletId: String, forced: Boolean): Flow<List<Raffle>> = flow {
        val key = cacheKey(walletId)
        seedFromDisk(key)
        if (forced) {
            scope.launch { loadRemote(walletId) }
        }
        emitAll(memoryCache.map { it[key] ?: emptyList() }.distinctUntilChanged())
    }.flowOn(Dispatchers.IO)

    private fun seedFromDisk(key: String) {
        if (memoryCache.value.containsKey(key)) {
            return
        }
        val disk = diskCache.getCache(key)?.distinctBy { it.id } ?: return
        memoryCache.update { current ->
            if (current.containsKey(key)) current else current + (key to disk)
        }
    }

    private fun cached(key: String): List<Raffle>? = memoryCache.value[key] ?: diskCache.getCache(key)

    private fun loadRemote(walletId: String): List<Raffle>? {
        val key = cacheKey(walletId)
        val isNew = !settingsRepository.hadWalletsOnMultichainRelease
        return try {
            val raffles = api.multichain.raffles
                .getWalletRaffles(walletId, lang, null, debugStore.debugNow, isNew)
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
}

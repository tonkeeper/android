package com.tonapps.wallet.data.earn

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.cache.JsonCacheRepository
import com.tonapps.wallet.data.cache.JsonResponseCacheScope
import com.tonapps.wallet.data.earn.entities.EarnAssetOfferEntity
import com.tonapps.wallet.data.earn.entities.EarnPortfolioState
import com.tonapps.wallet.data.earn.entities.EarnVaultEntity
import io.earnapi.models.Balance
import io.earnapi.models.Vault
import io.earnapi.models.Vaults
import io.infrastructure.ClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class EarnRepository(
    private val api: API,
    private val jsonCacheRepository: JsonCacheRepository,
    private val dirtyFlags: EarnPortfolioDirtyFlags,
) {

    private data class PortfolioKey(val walletId: String, val currency: String)

    private data class CachedPortfolio(val balance: Balance?, val failed: Boolean)

    private data object FeaturedVaultsKey

    private data class CachedVaults(val vaults: List<Vault>?, val failed: Boolean)

    private val memoryCache = MutableStateFlow<Map<PortfolioKey, CachedPortfolio>>(emptyMap())
    private val vaultsCache = MutableStateFlow<CachedVaults?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loads = ConcurrentHashMap<Any, Job>()

    suspend fun loadAssetOffer(assetId: String): EarnAssetOfferEntity? =
        loadAssetOffers(listOf(assetId))[assetId.lowercase()]

    suspend fun loadAssetOffers(assetIds: Collection<String>): Map<String, EarnAssetOfferEntity> =
        withContext(Dispatchers.IO) {
            val vaults = coroutineScope {
                assetIds.map { it.lowercase() }
                    .distinct()
                    .chunked(UNDERLYING_ASSET_IDS_LIMIT)
                    .map { chunk ->
                        async {
                            api.earn.vaults.getVaults(
                                currencies = listOf(EARN_USD.lowercase()),
                                underlyingAssetIds = chunk,
                            ).vaults
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            vaults.bestOffers()
        }

    fun getPortfolioFlow(
        walletId: String,
        currency: String,
        forced: Boolean,
        refresh: () -> Boolean,
    ): Flow<EarnPortfolioState?> = flow {
        val key = PortfolioKey(walletId, currency.uppercase())
        seedFromDisk(key)
        if (refresh()) {
            loads.remove(key)?.cancel()
            load(key) { loadRemote(key, refresh = true) }
        } else if (forced || memoryCache.value[key]?.failed != false) {
            load(key) { loadRemote(key, refresh = false) }
        }
        emitAll(
            memoryCache.map { it.portfolioFor(key) }
                .distinctUntilChanged()
                .map { it?.toState(key.currency) }
        )
    }.flowOn(Dispatchers.IO)

    fun getFeaturedVaultsFlow(forced: Boolean): Flow<List<EarnVaultEntity>?> = flow {
        seedVaultsFromDisk()
        if (forced || vaultsCache.value?.failed != false) {
            load(FeaturedVaultsKey) { loadVaultsRemote() }
        }
        emitAll(
            vaultsCache.map { cached -> cached?.let { it.vaults.orEmpty().map(Vault::toEntity) } }
        )
    }.flowOn(Dispatchers.IO)

    fun forgetWallet(walletId: String) {
        loads.keys.filter { it is PortfolioKey && it.walletId == walletId }.forEach { key -> loads[key]?.cancel() }
        memoryCache.update { current -> current.filterKeys { it.walletId != walletId } }
    }

    private suspend fun seedFromDisk(key: PortfolioKey) {
        if (key in memoryCache.value) {
            return
        }
        val json = try {
            jsonCacheRepository.getJson(
                scope = JsonResponseCacheScope.EARN_PORTFOLIO,
                cacheKey = key.currency,
                walletId = key.walletId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return
        }
        val balance = json?.let { EarnCacheJson.decode<Balance>(it) } ?: return
        updatePortfolio(key) { cached -> cached ?: CachedPortfolio(balance, failed = false) }
    }

    private suspend fun seedVaultsFromDisk() {
        if (vaultsCache.value != null) {
            return
        }
        val json = try {
            jsonCacheRepository.getJson(
                scope = JsonResponseCacheScope.EARN_VAULTS,
                cacheKey = FEATURED_VAULTS_CACHE_KEY,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return
        }
        val vaults = json?.let { EarnCacheJson.decode<Vaults>(it) } ?: return
        vaultsCache.update { cached -> cached ?: CachedVaults(vaults.vaults, failed = false) }
    }

    private suspend fun loadVaultsRemote() {
        val vaults = try {
            api.earn.vaults.getVaults(currencies = listOf(EARN_USD.lowercase()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            vaultsCache.update { cached -> CachedVaults(cached?.vaults, failed = true) }
            return
        }
        currentCoroutineContext().ensureActive()
        vaultsCache.value = CachedVaults(vaults.vaults, failed = false)
        try {
            jsonCacheRepository.save(
                scope = JsonResponseCacheScope.EARN_VAULTS,
                cacheKey = FEATURED_VAULTS_CACHE_KEY,
                json = EarnCacheJson.encode(vaults),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun load(key: Any, block: suspend () -> Unit) {
        loads.computeIfAbsent(key) {
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    loads.remove(key, coroutineContext.job)
                }
            }
        }.start()
    }

    private suspend fun loadRemote(key: PortfolioKey, refresh: Boolean) {
        val balance = try {
            api.earn.wallets.getWalletBalance(
                walletId = key.walletId,
                currencies = listOf(key.currency, EARN_USD).distinct().map { it.lowercase() },
                refresh = refresh || dirtyFlags.isDirty(key.walletId),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            FirebaseCrashlytics.getInstance().recordException(e.withCurrency(key.currency))
            updatePortfolio(key) { cached -> CachedPortfolio(cached?.balance, failed = true) }
            return
        }
        currentCoroutineContext().ensureActive()
        updatePortfolio(key) { CachedPortfolio(balance, failed = false) }
        try {
            jsonCacheRepository.save(
                scope = JsonResponseCacheScope.EARN_PORTFOLIO,
                cacheKey = key.currency,
                json = EarnCacheJson.encode(balance),
                walletId = key.walletId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun updatePortfolio(key: PortfolioKey, transform: (CachedPortfolio?) -> CachedPortfolio) {
        memoryCache.update { current -> current + (key to transform(current[key])) }
    }

    private fun Map<PortfolioKey, CachedPortfolio>.portfolioFor(key: PortfolioKey): CachedPortfolio? {
        val own = this[key]
        if (own?.balance != null) {
            return own
        }
        val other = entries.firstNotNullOfOrNull { (otherKey, cached) ->
            cached.balance?.takeIf { otherKey.walletId == key.walletId }
        } ?: return own
        return CachedPortfolio(other, failed = own?.failed == true)
    }

    private fun CachedPortfolio.toState(currency: String): EarnPortfolioState = if (balance != null) {
        EarnPortfolioState.Loaded(balance.toEntity(currency), isStale = failed)
    } else {
        EarnPortfolioState.Unavailable
    }

    private fun Exception.withCurrency(currency: String): Exception =
        if (this is ClientException && statusCode == HTTP_BAD_REQUEST) {
            IllegalStateException("Earn balance rejected currency $currency", this)
        } else {
            this
        }

    private companion object {

        private const val HTTP_BAD_REQUEST = 400
        private const val FEATURED_VAULTS_CACHE_KEY = "featured"
        private const val UNDERLYING_ASSET_IDS_LIMIT = 20
    }
}

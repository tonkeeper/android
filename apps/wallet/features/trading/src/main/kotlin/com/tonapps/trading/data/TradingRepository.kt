package com.tonapps.trading.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.db.FavoriteAssetDao
import com.tonapps.wallet.data.multichain.db.FavoriteAssetEntity
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint
import io.tradingapi.models.AssetDetailsResponse
import io.tradingapi.models.AssetsCatalogResponse
import io.tradingapi.models.AssetsSort
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketItem
import io.tradingapi.models.MultichainShelfGroup
import io.tradingapi.models.ShelfGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TradingRepository(
    private val api: API,
    private val environment: EnvironmentHelper,
    private val accountRepository: AccountRepository,
    private val favoriteAssetDao: FavoriteAssetDao,
) {
    sealed interface Keys : CacheKey {
        data object ShelvesMultichain : Keys
        data object ShelvesLegacy : Keys
    }

    private val cache = TimedCacheMemory<Keys>()
    private val marketItems = MutableStateFlow<Map<String, MarketItem>>(emptyMap())

    val favoriteAssetsFlow: StateFlow<List<MarketItem>> =
        accountRepository.selectedWalletFlow.flatMapLatest { wallet ->
            marketItems.value = emptyMap()
            favoriteAssetDao.observeIds(wallet.id)
                .onStart { loadFavoriteAssets(wallet.id) }
                .combine(marketItems) { ids, items ->
                    ids.mapNotNull { items[it] }
                }
        }.stateIn(
            scope = Async.ioScope(),
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    suspend fun isFavoriteAsset(assetId: String): Boolean = withContext(Async.Io) {
        val walletId = accountRepository.getSelectedWalletId() ?: return@withContext false
        favoriteAssetDao.isFavorite(walletId, assetId)
    }

    suspend fun addFavoriteAsset(assetId: String) = withContext(Async.Io) {
        val walletId = requireWalletId()
        favoriteAssetDao.insert(
            FavoriteAssetEntity(
                walletId = walletId,
                assetId = assetId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        fetchMarketItems(listOf(assetId)).firstOrNull()?.let { item ->
            marketItems.update { it + (assetId to item) }
        }
    }

    suspend fun removeFavoriteAsset(assetId: String) = withContext(Async.Io) {
        val walletId = requireWalletId()
        favoriteAssetDao.delete(walletId, assetId)
        marketItems.update { it - assetId }
    }

    suspend fun getShelfGroups(isMultichainWallet: Boolean): List<MultichainShelfGroup> {
        return if (isMultichainWallet) {
            cache.getOrLoad(Keys.ShelvesMultichain) {
                withContext(Async.Io) {
                    api.trading.shelves.getShelvesConfigV2(
                        xLang = environment.locale(),
                        storeCountryCode = environment.storeCountry(),
                        deviceCountryCode = environment.deviceCountry(),
                        simCountry = environment.simCountry(),
                        timezone = environment.timezone(),
                        isVpnActive = environment.isVpnActive(),
                        currency = environment.currency(),
                    ).groups.mapNotNull { multichainGroup ->
                        val groups = multichainGroup.groups.mapNotNull { it.pruned() }
                        if (groups.isEmpty()) {
                            null
                        } else {
                            multichainGroup.copy(groups = groups)
                        }
                    }
                }
            }
        } else {
            cache.getOrLoad(Keys.ShelvesLegacy) {
                withContext(Async.Io) {
                    api.trading.shelves.getShelvesConfig(
                        xLang = environment.locale(),
                        storeCountryCode = environment.storeCountry(),
                        deviceCountryCode = environment.deviceCountry(),
                        simCountry = environment.simCountry(),
                        timezone = environment.timezone(),
                        isVpnActive = environment.isVpnActive(),
                        currency = environment.currency(),
                    ).groups.mapNotNull { group ->
                        group.pruned()?.let {
                            MultichainShelfGroup(
                                id = it.stableId(),
                                name = it.name,
                                groups = listOf(it),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ShelfGroup.pruned(): ShelfGroup? {
        val items = items.filter { !it.items.isNullOrEmpty() }
        return if (items.isEmpty()) {
            null
        } else {
            copy(items = items)
        }
    }

    private fun ShelfGroup.stableId(): String {
        return (listOf(name) + items.map { it.key.value }).joinToString("|")
    }

    suspend fun getAssets(
        query: String,
        tab: AssetsTab,
        cursor: String?
    ): AssetsCatalogResponse = withContext(Async.Io) {
        api.trading.assets.getAssetsCatalog(
            q = query.ifBlank { null },
            tab = tab,
            sort = AssetsSort.volume_24h,
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

    suspend fun getAssetDetails(
        assetId: String,
        isMultichainWallet: Boolean
    ): AssetDetailsResponse = withContext(Async.Io) {
        if (isMultichainWallet) {
            api.trading.assets.getAssetDetailsV2(
                assetId = assetId,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                currency = environment.currency(),
            )
        } else {
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

    suspend fun getAssetChart(
        assetId: String,
        period: ChartPeriod,
        isMultichainWallet: Boolean
    ): List<ChartPoint> =
        withContext(Async.Io) {
            val endDate = System.currentTimeMillis() / 1000
            val startDate = endDate - period.durationSeconds
            val points = if (isMultichainWallet) {
                api.trading.assets.getAssetChartsV2(
                    assetId = assetId,
                    startDate = startDate,
                    endDate = endDate,
                    xLang = environment.locale(),
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                    currency = environment.currency(),
                )
            } else {
                api.trading.assets.getAssetCharts(
                    assetId = assetId,
                    startDate = startDate,
                    endDate = endDate,
                    xLang = environment.locale(),
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                    currency = environment.currency(),
                )
            }
            points.reversed().mapNotNull { point ->
                val date = point.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val price = point.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                ChartPoint(date = date, price = price)
            }
        }

    suspend fun clearCache() {
        cache.remove(Keys.ShelvesMultichain)
        cache.remove(Keys.ShelvesLegacy)
    }

    fun getCurrency(): String = environment.currency()

    private suspend fun loadFavoriteAssets(walletId: String): List<MarketItem> = withContext(Async.Io) {
        val ids = favoriteAssetDao.getAllIds(walletId)
        if (ids.isEmpty()) {
            marketItems.value = emptyMap()
            return@withContext emptyList()
        }

        val items = fetchMarketItems(ids)
        marketItems.value = items.associateBy { it.asset.id }
        items
    }

    private suspend fun fetchMarketItems(ids: List<String>): List<MarketItem> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        val response = api.trading.assets.getAssetsCatalogV2(
            ids = ids,
            xLang = environment.locale(),
            storeCountryCode = environment.storeCountry(),
            deviceCountryCode = environment.deviceCountry(),
            simCountry = environment.simCountry(),
            timezone = environment.timezone(),
            isVpnActive = environment.isVpnActive(),
            currency = environment.currency(),
        )
        return orderByIds(ids, response.items)
    }

    private fun requireWalletId(): String {
        return accountRepository.getSelectedWalletId()
            ?: error("Selected wallet is required for favorites")
    }

    private fun orderByIds(ids: List<String>, items: List<MarketItem>): List<MarketItem> {
        val itemsById = items.associateBy { it.asset.id }
        return ids.mapNotNull { itemsById[it] }
    }
}

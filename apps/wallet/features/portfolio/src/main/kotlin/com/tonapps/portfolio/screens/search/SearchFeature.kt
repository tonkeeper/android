package com.tonapps.portfolio.screens.search

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.flags.WalletFeature
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.perps.data.CATALOG_SEARCH_PAGE_SIZE
import com.tonapps.perps.data.CatalogSearchRepository
import com.tonapps.perps.data.CatalogSearchSort
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.perpsMarketsPager
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.Chain as WalletApiChain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class SearchFeature(
    data: SearchFeatureData,
    private val catalogSearchRepository: CatalogSearchRepository,
    private val accountRepoLegacy: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val perpsRepository: PerpsRepository,
    unifiedAccountRepository: UnifiedAccountRepository,
) : AsyncViewModel() {

    val sortOrder: StateFlow<CatalogSearchSort> field =
        MutableStateFlow(data.initialSort ?: CatalogSearchSort.MARKET_CAP)
    val queryFilter: StateFlow<String> field = MutableStateFlow("")
    val networkFilter: StateFlow<Network.Type?> field = MutableStateFlow(data.initialNetwork)
    val assetType: StateFlow<SearchAssetType> field = MutableStateFlow(SearchAssetType.ALL)
    val perpsSort: StateFlow<PerpsSort> field = MutableStateFlow(PerpsSort.VOLUME)

    @OptIn(FlowPreview::class)
    val debouncedQuery: StateFlow<String> = queryFilter.debounce(300).cacheState(initialValue = "")

    private val perpsGate: Flow<Boolean> = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.type == WalletType.Multichain && WalletFeature.Perps.isEnabled }
        .distinctUntilChanged()

    val isPerpsVisible: StateFlow<Boolean> = perpsGate.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val assetsFlow = combine(
        sortOrder,
        debouncedQuery,
        combine(networkFilter, perpsGate, assetType) { network, showPerps, type ->
            Triple(network, showPerps, type)
        }.distinctUntilChanged(),
        settingsRepository.currencyFlow,
        settingsRepository.safeModeChangedFlow,
    ) { sort, query, (network, showPerps, type), walletCurrency, _ ->
        SearchRequest(
            sort = sort,
            query = query,
            network = network,
            perps = showPerps,
            filter = type.toMarketFilter(),
            currency = walletCurrency.code,
            verifiedOnly = accountRepoLegacy.getSelectedWalletId()?.let { walletId ->
                settingsRepository.isSafeModeEnabled(walletId, TonNetwork.MAINNET)
            } ?: false,
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            Pager(
                config = PagingConfig(
                    initialLoadSize = CATALOG_SEARCH_PAGE_SIZE,
                    pageSize = CATALOG_SEARCH_PAGE_SIZE,
                    prefetchDistance = 10,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    SearchPagingSource(
                        repository = catalogSearchRepository,
                        query = request.query,
                        filter = request.filter,
                        sort = request.sort,
                        chain = request.network?.id?.let { WalletApiChain.decode(it)?.value },
                        showPerps = request.perps,
                        currency = request.currency,
                        verifiedOnly = request.verifiedOnly,
                    )
                },
            ).flow
        }
        .cachedIn(viewModelScope)

    val perpsCurrency: StateFlow<String> = settingsRepository.currencyFlow
        .map { it.code }
        .cacheState(initialValue = USD_CURRENCY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val perpsFlow: Flow<PagingData<PerpMarket>> = combine(
        debouncedQuery,
        perpsSort,
        settingsRepository.currencyFlow,
    ) { query, sort, walletCurrency -> Triple(query, sort, walletCurrency.code) }
        .distinctUntilChanged()
        .flatMapLatest { (query, sort, currency) ->
            perpsMarketsPager(
                repository = perpsRepository,
                query = query,
                filter = PerpsMarketFilter.ALL,
                sort = sort,
                currency = currency,
            ).flow
        }
        .cachedIn(viewModelScope)

    fun onSearch(query: String) {
        queryFilter.tryEmit(query)
    }

    fun onNetworkSelected(network: Network.Type?) {
        networkFilter.tryEmit(network)
    }

    fun onSortSelected(sort: CatalogSearchSort) {
        sortOrder.tryEmit(sort)
    }

    fun onAssetTypeSelected(type: SearchAssetType) {
        assetType.tryEmit(type)
    }

    fun onPerpsSortSelected(sort: PerpsSort) {
        perpsSort.tryEmit(sort)
    }
}

private fun SearchAssetType.toMarketFilter(): PerpsMarketFilter = when (this) {
    SearchAssetType.ALL, SearchAssetType.PERPETUALS -> PerpsMarketFilter.ALL
    SearchAssetType.TOKENS -> PerpsMarketFilter.TOKENS
    SearchAssetType.STOCKS -> PerpsMarketFilter.STOCKS
    SearchAssetType.ETFS -> PerpsMarketFilter.ETFS
}

private data class SearchRequest(
    val sort: CatalogSearchSort,
    val query: String,
    val network: Network.Type?,
    val perps: Boolean,
    val filter: PerpsMarketFilter,
    val currency: String,
    val verifiedOnly: Boolean,
)

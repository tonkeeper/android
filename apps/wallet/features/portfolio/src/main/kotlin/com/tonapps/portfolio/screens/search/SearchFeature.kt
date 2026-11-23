package com.tonapps.portfolio.screens.search

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.flags.WalletFeature
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.perps.data.CATALOG_SEARCH_PAGE_SIZE
import com.tonapps.perps.data.CatalogSearchRepository
import com.tonapps.perps.data.CatalogSearchRow
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.flow.update

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
    val availableNetworks: StateFlow<ImmutableList<Network.Type>?> field = MutableStateFlow(null)
    val assetType: StateFlow<SearchAssetType> field = MutableStateFlow(data.initialAssetType ?: SearchAssetType.ALL)
    val perpsSort: StateFlow<PerpsSort> field = MutableStateFlow(PerpsSort.VOLUME)

    @OptIn(FlowPreview::class)
    val debouncedQuery: StateFlow<String> = queryFilter.map { it.trim() }.debounce(300).cacheState(initialValue = "")

    private val perpsGate: Flow<Boolean> = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.supportsPerps == true && WalletFeature.Perps.isEnabled }
        .distinctUntilChanged()

    val isPerpsVisible: StateFlow<Boolean> = perpsGate.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val assetsFlow: Flow<PagingData<SearchListItem>> = combine(
        sortOrder,
        debouncedQuery,
        combine(networkFilter, perpsGate, assetType) { network, showPerps, type ->
            Triple(network, showPerps, type.takeIf { it != SearchAssetType.PERPETUALS } ?: SearchAssetType.ALL)
        }.distinctUntilChanged(),
        settingsRepository.currencyFlow,
        settingsRepository.safeModeChangedFlow,
    ) { sort, query, (network, showPerps, type), walletCurrency, _ ->
        SearchRequest(
            sort = sort,
            query = query,
            network = network,
            perps = showPerps,
            type = type,
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
                        onAvailableNetworksLoaded = { networks ->
                            onAvailableNetworksLoaded(request.filter, networks)
                        },
                    )
                },
            ).flow.map { data ->
                data.map { SearchListItem.Row(it) }
                    .insertSeparators<SearchListItem.Row, SearchListItem> { before, after ->
                        when {
                            request.isSectioned -> sectionHeader(before?.row, after?.row)
                            before == null && after != null ->
                                SearchListItem.Header(request.type.listSection(), after.row.id)
                            else -> null
                        }
                    }
            }
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

    private fun onAvailableNetworksLoaded(filter: PerpsMarketFilter, networks: List<Network.Type>?) {
        if (filter != assetType.value.toMarketFilter()) {
            return
        }
        availableNetworks.tryEmit(networks?.toImmutableList())
        if (networks != null) {
            networkFilter.update { selected -> selected?.takeIf { it in networks } }
        }
    }
}

private fun SearchAssetType.toMarketFilter(): PerpsMarketFilter = when (this) {
    SearchAssetType.ALL, SearchAssetType.PERPETUALS -> PerpsMarketFilter.ALL
    SearchAssetType.TOKENS -> PerpsMarketFilter.TOKENS
    SearchAssetType.COMMODITIES -> PerpsMarketFilter.COMMODITIES
    SearchAssetType.STOCKS -> PerpsMarketFilter.STOCKS
    SearchAssetType.ETFS -> PerpsMarketFilter.ETFS
}

private data class SearchRequest(
    val sort: CatalogSearchSort,
    val query: String,
    val network: Network.Type?,
    val perps: Boolean,
    val type: SearchAssetType,
    val filter: PerpsMarketFilter,
    val currency: String,
    val verifiedOnly: Boolean,
) {
    val isSectioned: Boolean
        get() = perps && network == null && filter == PerpsMarketFilter.ALL && query.isNotEmpty()
}

private fun SearchAssetType.listSection(): SearchSection = when (this) {
    SearchAssetType.ALL -> SearchSection.ALL_ASSETS
    SearchAssetType.TOKENS -> SearchSection.TOKENS
    SearchAssetType.COMMODITIES -> SearchSection.COMMODITIES
    SearchAssetType.STOCKS -> SearchSection.STOCKS
    SearchAssetType.ETFS -> SearchSection.ETFS
    SearchAssetType.PERPETUALS -> SearchSection.PERPETUALS
}

private fun sectionHeader(before: CatalogSearchRow?, after: CatalogSearchRow?): SearchListItem.Header? {
    return when {
        after is CatalogSearchRow.Perp && before !is CatalogSearchRow.Perp ->
            SearchListItem.Header(SearchSection.PERPETUALS, after.id)
        after is CatalogSearchRow.Spot && before == null ->
            SearchListItem.Header(SearchSection.TOKENS, after.id)
        else -> null
    }
}

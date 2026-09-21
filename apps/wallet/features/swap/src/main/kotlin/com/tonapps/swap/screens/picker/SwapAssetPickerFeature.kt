package com.tonapps.swap.screens.picker

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.paging.AccountsPagingCacheSession
import com.tonapps.core.paging.AccountsPagingSource
import com.tonapps.wallet.data.multichain.account.AssetCapability
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.swap.SwapRoutes
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest

class SwapAssetPickerFeature(
    private val side: SwapRoutes.SelectAsset.Side,
    private val accountRepoLegacy: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val selectedFilter: StateFlow<Network.Type?> field = MutableStateFlow(null)
    val queryFilter: StateFlow<String> field = MutableStateFlow("")

    private val availableOnly = side == SwapRoutes.SelectAsset.Side.Send
    private val showAll = side == SwapRoutes.SelectAsset.Side.Receive
    private val accountsPagingCacheSession = AccountsPagingCacheSession()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val accountsFlow = combine(
        queryFilter.debounce(300),
        selectedFilter,
        settingsRepository.currencyFlow,
    ) { query, network, walletCurrency ->
        Triple(query, network, walletCurrency.code)
    }
        .flatMapLatest { (query, network, currencyCode) ->
            val walletId = accountRepoLegacy.getSelectedWalletId().orEmpty()

            Pager(
                config = PagingConfig(
                    initialLoadSize = 25,
                    pageSize = 25,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    AccountsPagingSource(
                        accountRepo = accountRepo,
                        walletId = walletId,
                        currency = currencyCode,
                        query = query.takeIf { it.isNotBlank() },
                        network = network?.id,
                        availableOnly = availableOnly,
                        showHidden = showAll,
                        showAll = showAll,
                        cacheSession = accountsPagingCacheSession,
                        scope = viewModelScope,
                        capabilities = listOf(AssetCapability.Swap),
                    )
                },
            ).flow
        }
        .cachedIn(viewModelScope)

    fun selectFilter(networkId: Network.Type?) {
        selectedFilter.tryEmit(networkId)
    }

    fun onSearch(query: String) {
        queryFilter.tryEmit(query)
    }
}

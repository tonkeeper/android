package com.tonapps.deposit.multicoin.screens.picker

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.deposit.multicoin.analytics.WithdrawAnalytics
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.util.concurrent.atomic.AtomicBoolean

class AssetPickerFeature(
    private val analyticsFrom: WithdrawFlowFrom,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val selectedFilter: StateFlow<Network.Type?> field = MutableStateFlow(null)
    val queryFilter: StateFlow<String?> field = MutableStateFlow(null)

    val analytics = WithdrawAnalytics(analyticsFrom)

    // The pager is rebuilt on every search/chain filter change; the screen is only "viewed" once.
    private val viewTracked = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val accountsFlow = combine(
        selectedFilter,
        queryFilter.debounce(200).distinctUntilChanged(),
        settingsRepository.currencyFlow,
    ) { network, query, walletCurrency -> Triple(network, query, walletCurrency.code) }
        .distinctUntilChanged()
        .flatMapLatest { (network, query, currencyCode) ->
            Pager(
                config = PagingConfig(
                    initialLoadSize = 25,
                    pageSize = 25,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                    maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
                ),
                pagingSourceFactory = {
                    AssetPickerPagingSource(
                        oldAccount = oldAccount,
                        accountRepo = accountRepo,
                        currency = currencyCode,
                        query = query,
                        network = network?.id,
                        onFirstPageLoaded = ::trackView,
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

    private fun trackView(accounts: List<AccountWithDetails>) {
        if (viewTracked.compareAndSet(false, true)) {
            analytics.viewChooseAsset(accounts.map { it.asset.id })
        }
    }
}

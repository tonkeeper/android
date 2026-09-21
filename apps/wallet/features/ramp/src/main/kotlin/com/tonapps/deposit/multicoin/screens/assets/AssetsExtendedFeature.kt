package com.tonapps.deposit.multicoin.screens.assets

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.deposit.multicoin.analytics.RampAnalytics
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

data class AssetsExtendedFeatureData(
    val rampType: RampType,
    val analyticsFrom: DepositFlowFrom,
    val preferredCurrency: String? = null,
)

class AssetsExtendedFeature(
    private val data: AssetsExtendedFeatureData,
    private val rampRepository: RampRepository,
) : AsyncViewModel() {

    val selectedFilter: StateFlow<Network.Type?> field = MutableStateFlow(null)
    val queryFilter: StateFlow<String?> field = MutableStateFlow(null)

    val chainFilters: StateFlow<List<Chain>> = when (data.rampType) {
        RampType.RampOn -> flow { emit(rampRepository.getOnrampChains(fiat = data.preferredCurrency)) }
            .catch { e ->
                L.e(e, "Failed to load onramp chains")
                emit(Chain.all.toList())
            }
            .cacheState(initialValue = emptyList())

        RampType.RampOff -> MutableStateFlow(Chain.all.toList())
    }

    private val analytics = RampAnalytics.of(data.rampType, data.analyticsFrom)

    // The pager is rebuilt on every search/chain filter change; the screen is only "viewed" once.
    private val viewTracked = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val assetsFlow = combine(
        selectedFilter,
        queryFilter.debounce(200).distinctUntilChanged(),
    ) { network, query -> network to query }
        .distinctUntilChanged()
        .flatMapLatest { (network, query) ->
            Pager(
                config = PagingConfig(
                    initialLoadSize = 25,
                    pageSize = 25,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                    maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
                ),
                pagingSourceFactory = {
                    AssetsExtendedPagingSource(
                        rampRepository = rampRepository,
                        rampType = data.rampType,
                        query = query,
                        fiat = data.preferredCurrency,
                        chain = network?.id,
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
        queryFilter.tryEmit(query.takeIf { it.isNotBlank() })
    }

    private fun trackView(assets: List<RampAsset>) {
        if (!viewTracked.compareAndSet(false, true)) {
            return
        }

        analytics.viewFiatChooseAsset(assets.map { it.asset.id })
    }
}

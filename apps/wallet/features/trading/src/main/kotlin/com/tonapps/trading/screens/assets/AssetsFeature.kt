package com.tonapps.trading.screens.assets

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.trading.TradeEntryTracker
import com.tonapps.trading.data.TradingRepository
import io.tradingapi.models.AssetsTab

sealed interface AssetsAction : MviAction {
    data object Init : AssetsAction
    data class Search(val query: String, val tab: AssetsTab) : AssetsAction
    data object LoadMore : AssetsAction
}

sealed interface AssetsState : MviState {
    data object Loading : AssetsState
    data object Error : AssetsState
    data class Data(
        val items: List<AssetItem>,
        val nextCursor: String?,
        val isLoadingMore: Boolean,
        val query: String,
        val tab: AssetsTab,
    ) : AssetsState {
        val hasMore: Boolean get() = nextCursor != null
    }
}

class AssetsViewState(
    val global: MviProperty<AssetsState>,
) : MviViewState

class AssetsFeature(
    private val tradingRepository: TradingRepository,
    private val environment: EnvironmentHelper,
    val initialTab: AssetsTab = AssetsTab.all,
) : MviFeature<AssetsAction, AssetsState, AssetsViewState>(
    initState = AssetsState.Loading,
    initAction = AssetsAction.Init,
) {

    override fun createViewState(): AssetsViewState {
        return buildViewState {
            AssetsViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: AssetsAction) {
        when (action) {
            AssetsAction.Init -> load(query = "", tab = initialTab)
            is AssetsAction.Search -> {
                load(query = action.query, tab = action.tab)
            }

            AssetsAction.LoadMore -> loadMore()
        }
    }

    private suspend fun load(query: String, tab: AssetsTab) {
        setState { AssetsState.Loading }
        try {
            val currencyCode = environment.currency()
            val response = tradingRepository.getAssets(query, tab, cursor = null)
            setState {
                AssetsState.Data(
                    items = response.items.map { it.toAssetItem(currencyCode) },
                    nextCursor = response.nextCursor,
                    isLoadingMore = false,
                    query = query,
                    tab = tab,
                )
            }
            AnalyticsHelper.Default.events.tradeUiFlow.tradeSearch(
                from = TradeEntryTracker.consumeFrom(),
                query = query.ifBlank { null }
            )
        } catch (e: Throwable) {
            L.e(e)
            setState { AssetsState.Error }
        }
    }

    private suspend fun loadMore() {
        val current = obtainSpecificState<AssetsState.Data>() ?: return
        if (current.nextCursor == null || current.isLoadingMore) return
        setState { current.copy(isLoadingMore = true) }
        try {
            val currencyCode = environment.currency()
            val response =
                tradingRepository.getAssets(current.query, current.tab, current.nextCursor)
            setState {
                val data = this as? AssetsState.Data ?: return@setState this
                data.copy(
                    items = data.items + response.items.map { it.toAssetItem(currencyCode) },
                    nextCursor = response.nextCursor,
                    isLoadingMore = false,
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            setState {
                val data = this as? AssetsState.Data ?: return@setState this
                data.copy(isLoadingMore = false)
            }
        }
    }

    fun trackAssetClick(asset: AssetItem) {
        val data = obtainSpecificState<AssetsState.Data>() ?: return
        AnalyticsHelper.Default.events.tradeUiFlow.tradeSearchClick(
            from = TradeEntryTracker.consumeFrom(),
            asset = asset.id,
            query = data.query.ifBlank { null }
        )
    }
}

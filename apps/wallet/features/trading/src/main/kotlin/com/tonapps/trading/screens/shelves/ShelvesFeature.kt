package com.tonapps.trading.screens.shelves

import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.trading.data.TradingRepository
import io.tradingapi.models.ShelfGroup

sealed interface ShelvesAction : MviAction {
    data object Init : ShelvesAction
    data object Retry : ShelvesAction
}

sealed interface ShelvesState : MviState {
    data object Loading : ShelvesState
    data object Empty : ShelvesState
    data class Data(
        val shelfGroups: List<ShelfGroup>,
    ) : ShelvesState
}

class ShelvesViewState(
    val global: MviProperty<ShelvesState>,
) : MviViewState

class ShelvesFeature(
    private val tradingRepository: TradingRepository,
) : MviFeature<ShelvesAction, ShelvesState, ShelvesViewState>(
    initState = ShelvesState.Loading,
    initAction = ShelvesAction.Init,
) {

    override fun createViewState(): ShelvesViewState {
        return buildViewState {
            ShelvesViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: ShelvesAction) {
        when (action) {
            ShelvesAction.Init, ShelvesAction.Retry -> load()
        }
    }

    private suspend fun load() {
        setState { ShelvesState.Loading }
        try {
            val shelfGroups = tradingRepository.getShelfGroups()
            if (shelfGroups.isEmpty()) {
                setState { ShelvesState.Empty }
            } else {
                setState { ShelvesState.Data(shelfGroups = shelfGroups) }
            }
        } catch (e: Throwable) {
            L.e(e)
            setState { ShelvesState.Empty }
        }
    }
}

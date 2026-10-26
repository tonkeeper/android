package com.tonapps.wallet.features.events.screens.market

import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.McEventsRepository
import com.tonapps.wallet.features.events.screens.WalletActivityListItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

interface PerpsMarketHistoryViewState : MviViewState {
    val items: StateFlow<List<WalletActivityListItem.Entry>>
}

sealed interface PerpsMarketHistoryAction : MviAction {
    data class Reload(val attempt: Int) : PerpsMarketHistoryAction
}

class PerpsMarketHistoryFeature(
    private val walletId: String,
    private val marketIndex: Int,
    private val eventsRepository: McEventsRepository,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsMarketHistoryViewState, PerpsMarketHistoryAction>(), PerpsMarketHistoryViewState {

    private val itemsState = actions
        .on<PerpsMarketHistoryAction.Reload>()
        .map { it.attempt }
        .distinctUntilChanged()
        .transformStateLatest {
            val hiddenBalance = settingsRepository.hiddenBalances
            runCatching { eventsRepository.getRecentMarketActivities(walletId, marketIndex) }
                .getOrNull()
                ?.map { WalletActivityListItem.Entry(activity = it, hiddenBalance = hiddenBalance) }
        }
        .cacheStateWithLoading()

    override val items = itemsState
        .mapNotNull { it.state }
        .cacheState(initialValue = emptyList())
}

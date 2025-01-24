package com.tonapps.tonkeeper.ui.screen.events.spam

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SpamEventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _eventsFlow = MutableStateFlow<List<AccountEvent>?>(null)
    private val eventsFlow = _eventsFlow.asStateFlow().filterNotNull()
    private val _eventsList: List<AccountEvent>
        get() = _eventsFlow.value ?: emptyList()

    private val _isLoadingFlow = MutableStateFlow(true)
    private val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val uiItemsFlow = eventsFlow.map {
        historyHelper.mapping(
            wallet = wallet,
            events = it,
            options = ActionOptions(
                spamFilter = ActionOptions.SpamFilter.SPAM,
                hiddenBalances = settingsRepository.hiddenBalances,
            )
        )
    }.map {
        historyHelper.groupByDate(it)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val uiStateFlow = combine(
        uiItemsFlow,
        isLoadingFlow
    ) { uiItems, isLoading ->
        SpamUiState(
            uiItems = if (isLoading && uiItems.isNotEmpty()) {
                historyHelper.withLoadingItem(uiItems)
            } else if (isLoading || (uiItems.isEmpty() && _eventsList.isNotEmpty())) {
                listOf(HistoryItem.Loader(0, 0L))
            } else if (_eventsList.isEmpty()) {
                listOf(HistoryItem.Empty(context, Localization.spam_folder_empty))
            } else {
                historyHelper.removeLoadingItem(uiItems)
            },
            loading = isLoading
        )
    }.flowOn(Dispatchers.Main).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SpamUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            init()
        }

        settingsRepository.walletPrefsChangedFlow.drop(1).collectFlow {
            init()
        }
    }

    private suspend fun init() {
        _eventsFlow.value = getLocalSpam()

        if (20 >= _eventsList.size) {
            mergeEvents(getRemoteSpam())
        } else {
            _isLoadingFlow.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingFlow.value) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentEvents = _eventsFlow.value ?: return@launch
            if (currentEvents.isEmpty()) {
                return@launch
            }
            val lastLt = currentEvents.last().lt

            _isLoadingFlow.value = true
            mergeEvents(getRemoteSpam(lastLt))
        }
    }

    private fun mergeEvents(events: List<AccountEvent>) {
        _eventsFlow.value = (_eventsList + events).distinctBy {
            it.eventId
        }
        _isLoadingFlow.value = false
    }

    private suspend fun getLocalSpam() = eventsRepository.getLocalSpam(
        accountId = wallet.accountId,
        testnet = wallet.testnet
    )

    private suspend fun getRemoteSpam(startBeforeLt: Long? = null) = eventsRepository.getRemoteSpam(
        accountId = wallet.accountId,
        testnet = wallet.testnet,
        startBeforeLt = startBeforeLt
    )

}
package com.tonapps.tonkeeper.ui.screen.events.spam

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpamEventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val remoteConfig: RemoteConfig,
) : BaseWalletVM(app) {

    private val _eventsFlow = MutableStateFlow<List<AccountEvent>?>(null)
    private val eventsFlow = _eventsFlow.asStateFlow().filterNotNull()
    private val _eventsList: List<AccountEvent>
        get() = _eventsFlow.value ?: emptyList()

    private val _isLoadingFlow = MutableStateFlow(true)
    private val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val _tronEventsFlow = MutableStateFlow<List<TronEventEntity>?>(null)
    private val tronEventsFlow = _tronEventsFlow.asStateFlow().filterNotNull()
    private val _tronEventsList: List<TronEventEntity>
        get() = _tronEventsFlow.value ?: emptyList()

    private fun getTronLastLt(): Long? {
        val lt = _tronEventsFlow.value?.lastOrNull { !it.inProgress }?.timestamp ?: return null
        if (0 >= lt) {
            return null
        }
        return lt
    }

    private val uiItemsFlow = combine(eventsFlow, tronEventsFlow) { events, tronEvents ->
        (historyHelper.mapping(
            wallet = wallet,
            events = events,
            options = ActionOptions(
                spamFilter = ActionOptions.SpamFilter.SPAM,
                hiddenBalances = settingsRepository.hiddenBalances,
                tronEnabled = settingsRepository.getTronUsdtEnabled(wallet.id),
            )
        ) + tronMapping(tronEvents)).sortedBy { it.timestampForSort }
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
            } else if (isLoading || (uiItems.isEmpty() && _eventsList.isNotEmpty() && _tronEventsList.isNotEmpty())) {
                listOf(HistoryItem.Loader(0, 0L))
            } else if (_eventsList.isEmpty() && _tronEventsList.isEmpty()) {
                listOf(HistoryItem.Empty(context, Localization.spam_folder_empty))
            } else {
                historyHelper.removeServiceItems(uiItems)
            },
            loading = isLoading
        )
    }.flowOn(Dispatchers.Main)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SpamUiState())

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

        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet && !remoteConfig.isTronDisabled) {
            accountRepository.getTronAddress(wallet.id)
        } else null
        _tronEventsFlow.value =
            tronAddress?.let { eventsRepository.getTronLocal(tronAddress) } ?: emptyList()

        if (tronAddress != null) {
            val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: ""
            _tronEventsFlow.value =
                eventsRepository.loadTronEvents(tronAddress, tonProofToken, limit = 50)
        }

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
            loadMoreTron()
            mergeEvents(getRemoteSpam(lastLt))
        }
    }

    private suspend fun loadMoreTron() {
        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet && !remoteConfig.isTronDisabled) {
            accountRepository.getTronAddress(wallet.id)
        } else null

        if (tronAddress.isNullOrEmpty()) {
            return
        }

        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: ""
        val currentEvents = (_tronEventsFlow.value ?: emptyList())
        try {
            val tronLastLt = getTronLastLt()
            if (tronLastLt != null) {
                val tronEvents = eventsRepository.loadTronEvents(
                    tronAddress,
                    tonProofToken,
                    beforeLt = tronLastLt
                ) ?: throw IllegalStateException("Failed to load tron events")
                val events = (currentEvents + tronEvents).distinctBy { it.transactionHash }
                    .sortedBy { it.timestamp }.reversed()
                _tronEventsFlow.value = events
            }
        } catch (_: Throwable) {
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

    private suspend fun tronMapping(events: List<TronEventEntity>): List<HistoryItem> {
        val tronAddress = accountRepository.getTronAddress(wallet.id) ?: ""
        val eventItems = historyHelper.tronMapping(
            wallet = wallet,
            events = events.map { it.copy() },
            tronAddress = tronAddress,
            options = ActionOptions(
                spamFilter = ActionOptions.SpamFilter.SPAM,
                hiddenBalances = settingsRepository.hiddenBalances,
            )
        )
        return eventItems
    }

}
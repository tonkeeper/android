package com.tonapps.tonkeeper.ui.screen.events.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.mapList
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.ActionOutStatus
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.extensions.notificationsFlow
import com.tonapps.tonkeeper.extensions.refreshNotifications
import com.tonapps.tonkeeper.helper.CacheHelper
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.events.main.filters.FilterItem
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

// TODO: Refactor this class
class EventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
    private val dAppsRepository: DAppsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val cacheHelper: CacheHelper,
    private val remoteConfig: RemoteConfig,
) : BaseWalletVM(app) {

    private var autoRefreshJob: Job? = null

    private val _triggerFlow = MutableEffectFlow<Unit>()
    private val _loadingTriggerFlow = MutableEffectFlow<Unit>()

    private val _selectedFilter = MutableStateFlow<FilterItem?>(null)
    private val selectedFilter = _selectedFilter.asStateFlow()

    private val dAppsNotificationsFlow =
        dAppsRepository.notificationsFlow(wallet, viewModelScope).map {
            it.notifications
        }

    val uiFilterItemsFlow: Flow<List<FilterItem>> =
        combine(selectedFilter, dAppsNotificationsFlow) { selected, notifications ->
            val uiFilterItems = mutableListOf<FilterItem>()
            uiFilterItems.add(FilterItem.Send(selected?.type == FilterItem.TYPE_SEND))
            uiFilterItems.add(FilterItem.Receive(selected?.type == FilterItem.TYPE_RECEIVE))
            if (notifications.isNotEmpty()) {
                uiFilterItems.add(FilterItem.Dapps(selected?.type == FilterItem.TYPE_DAPPS))
            }
            uiFilterItems.add(FilterItem.Spam())
            uiFilterItems.toList()
        }

    private val isLoading: AtomicBoolean = AtomicBoolean(false)
    private val isError: AtomicBoolean = AtomicBoolean(false)

    private var nextFrom: Long? = null

    private val isFirstLoad = AtomicBoolean(true)

    private val _eventsFlow = MutableStateFlow<Array<AccountEventWrap>?>(null)

    private val eventsFlow = _eventsFlow.asStateFlow().filterNotNull()

    private val _tronEventsFlow = MutableStateFlow<List<TronEventEntity>>(emptyList())

    private val tronEventsFlow = _tronEventsFlow.asStateFlow().filterNotNull()

    private val historyItemsFlow = combine(
        eventsFlow.map { list -> list.map { it.event } },
        tronEventsFlow,
        _triggerFlow,
        dAppsNotificationsFlow.mapList { HistoryItem.App(context, wallet, it) },
    ) { events, tronEvents, _, dAppNotifications ->
        val historyEvents = mapping(events)
        val lastEventsTimestamp = historyEvents.lastOrNull()?.timestampForSort ?: 0L
        val tronHistoryEvents = if (nextFrom != null) {
            tronMapping(tronEvents).filter { it.timestampForSort > lastEventsTimestamp }
        } else {
            tronMapping(tronEvents)
        }

        val hasFilter = _selectedFilter.value != null


        if (hasFilter) {
            (historyEvents + tronHistoryEvents + dAppNotifications).sortedBy { it.timestampForSort }
        } else {
            val lastTronTimestamp = tronHistoryEvents.lastOrNull()?.timestampForSort ?: 0L
            val lastTimestamp = min(lastEventsTimestamp, lastTronTimestamp)
            (historyEvents + tronHistoryEvents + dAppNotifications.filter { it.timestamp > lastTimestamp }).sortedBy { it.timestampForSort }
        }
    }

    private val uiItemsFlow = combine(
        historyItemsFlow, selectedFilter, _loadingTriggerFlow
    ) { historyItems, filter, _ ->
        val actionOutStatus = resolveActionOutStatus(resolveFilter(filter))
        val uiItems = historyHelper.groupByDate(historyItems.filter {
            when (actionOutStatus) {
                ActionOutStatus.dApps -> it is HistoryItem.App
                ActionOutStatus.App -> it is HistoryItem.App && it.url == (filter as? FilterItem.App)?.url
                ActionOutStatus.Send -> it is HistoryItem.Event && (it.actionOutStatus == ActionOutStatus.Send || it.actionOutStatus == ActionOutStatus.Any)
                ActionOutStatus.Received -> it is HistoryItem.Event && (it.actionOutStatus == ActionOutStatus.Received || it.actionOutStatus == ActionOutStatus.Any)
                else -> true
            }
        })
        val list = if (isError.get()) {
            historyHelper.withFailedItem(uiItems)
        } else if (isLoading.get()) {
            historyHelper.withLoadingItem(uiItems)
        } else {
            historyHelper.removeServiceItems(uiItems)
        }
        if (actionOutStatus == ActionOutStatus.Any || actionOutStatus == null) {
            cacheHelper.setEventsCached(wallet, list)
        }
        list
    }.flowOn(Dispatchers.IO)

    val installId: String
        get() = settingsRepository.installId

    val uiStateFlow: Flow<EventsUiState> = flow {
        val cached = cacheHelper.getEventsCached(wallet)
        if (cached.isNotEmpty()) {
            emit(
                EventsUiState(
                    uiItems = cached, loading = true
                )
            )
        }

        emitAll(uiItemsFlow.map {
            EventsUiState(
                uiItems = it,
                loading = isLoading.get(),
            )
        })
    }

    init {
        with(settingsRepository) {
            tokenPrefsChangedFlow.drop(1).collectFlow { initialLoad() }
            walletPrefsChangedFlow.drop(1).collectFlow { initialLoad() }
            safeModeStateFlow.drop(1).collectFlow { initialLoad() }
            hiddenBalancesFlow.drop(1).collectFlow { updateState() }
        }
        transactionManager.eventsFlow(wallet).drop(1).collectFlow { initialLoad() }
        transactionManager.tronUpdatedFlow.drop(1).collectFlow { refreshTron() }
        eventsRepository.decryptedCommentFlow.collectFlow { updateState() }
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                checkAutoRefresh()
                delay(35.seconds)
            }
        }

        viewModelScope.launch {
            dAppsRepository.refreshNotifications(wallet, accountRepository)
        }
        _triggerFlow.tryEmit(Unit)
    }

    private fun setError() {
        isError.set(true)
        if (_eventsFlow.value == null) {
            _eventsFlow.value = emptyArray()
            setLoading(loading = false, trigger = false)
        } else {
            setLoading(loading = false, trigger = true)
        }
    }

    private fun setLoading(loading: Boolean, trigger: Boolean) {
        isLoading.set(loading)
        if (trigger) {
            _loadingTriggerFlow.tryEmit(Unit)
        }
    }

    private fun resolveFilter(item: FilterItem?): Int {
        return if (item == null) {
            TX_FILTER_NONE
        } else {
            when (item.id) {
                FilterItem.SEND_ID -> TX_FILTER_SENT
                FilterItem.RECEIVE_ID -> TX_FILTER_RECEIVED
                FilterItem.DAPPS_ID -> TX_FILTER_DAPPS
                else -> TX_FILTER_APP
            }
        }
    }

    private fun resolveActionOutStatus(filter: Int): ActionOutStatus? {
        return when (filter) {
            // TX_FILTER_APP -> ActionOutStatus.App
            TX_FILTER_SENT -> ActionOutStatus.Send
            TX_FILTER_RECEIVED -> ActionOutStatus.Received
            TX_FILTER_DAPPS -> ActionOutStatus.dApps
            else -> return null
        }
    }

    fun clickFilter(filter: FilterItem) {
        if (_selectedFilter.value?.id == filter.id) {
            _selectedFilter.value = null
        } else {
            _selectedFilter.value = filter
        }
    }

    private fun checkAutoRefresh() {
        if (hasPendingEvents()) {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            initialLoad()
        }
    }

    private suspend fun refreshTron() = withContext(Dispatchers.IO) {
        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet) {
            accountRepository.getTronAddress(wallet.id)
        } else null
        val tonProofToken = accountRepository.requestTonProofToken(wallet)

        if (tronAddress == null || tonProofToken == null) {
            return@withContext
        }

        val tronEvents =
            eventsRepository.loadTronEvents(tronAddress, tonProofToken) ?: return@withContext

        _tronEventsFlow.update { items ->
            (items + tronEvents).distinctBy { it.transactionHash }.sortedBy { it.timestamp }
                .reversed()
        }
    }

    private suspend fun initialLoad() =
        withContext(Dispatchers.IO) {
            if (isLoading.get()) {
                return@withContext
            }

            setLoading(loading = true, trigger = true)

            val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet && !remoteConfig.isTronDisabled) {
                accountRepository.getTronAddress(wallet.id)
            } else null
            val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: ""

            if (isFirstLoad.get()) {
                val cached = cache().toTypedArray()
                _tronEventsFlow.value =
                    tronAddress?.let { eventsRepository.getTronLocal(tronAddress) } ?: emptyList()
                if (cached.isNotEmpty()) {
                    _eventsFlow.value = cached
                }

                isFirstLoad.set(false)
            }

            try {
                val events = loadDefault(beforeLt = null, limit = 30).toTypedArray()
                val tronEvents =
                    tronAddress?.let { eventsRepository.loadTronEvents(tronAddress, tonProofToken) }
                        ?: emptyList()
                isError.set(false)
                setLoading(loading = false, trigger = false)

                _eventsFlow.value = events
                _tronEventsFlow.value = tronEvents
            } catch (e: Throwable) {
                setError()
            }
        }

    fun loadMore() {
        if (isLoading.get() || _selectedFilter.value is FilterItem.App || _selectedFilter.value is FilterItem.Dapps) {
            return
        }

        setLoading(loading = true, trigger = true)
        viewModelScope.launch(Dispatchers.IO) {
            val currentEvents = (_eventsFlow.value?.toMutableList() ?: mutableListOf())
            try {
                val events = if (nextFrom != null) {
                    val beforeLtEvents = loadDefault(
                        beforeLt = nextFrom, limit = 30
                    )
                    (currentEvents + beforeLtEvents).distinctBy { it.eventId }
                        .sortedBy { it.timestamp }.reversed()
                } else null

                loadMoreTron()

                if (events != null) {
                    _eventsFlow.value = events.toTypedArray()
                }

                isError.set(false)
                setLoading(loading = false, trigger = false)
            } catch (e: Throwable) {
                setError()
            }
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
        try {
            val tronLastLt = getTronLastLt()
            if (tronLastLt != null) {
                val tronEvents = eventsRepository.loadTronEvents(
                    tronAddress, tonProofToken, beforeLt = tronLastLt
                ) ?: throw IllegalStateException("Failed to load tron events")

                _tronEventsFlow.update { currentEvents ->
                    (currentEvents + tronEvents).distinctBy { it.transactionHash }
                        .sortedBy { it.timestamp }.reversed()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun mapping(events: List<AccountEvent>): List<HistoryItem> {
        val eventItems = historyHelper.mapping(
            wallet = wallet, events = events.map { it.copy() }, options = ActionOptions(
                spamFilter = ActionOptions.SpamFilter.NOT_SPAM,
                safeMode = settingsRepository.isSafeModeEnabled(api),
                hiddenBalances = settingsRepository.hiddenBalances,
                tronEnabled = settingsRepository.getTronUsdtEnabled(wallet.id),
            )
        )
        return eventItems
    }

    private suspend fun tronMapping(events: List<TronEventEntity>): List<HistoryItem> {
        val tronAddress = accountRepository.getTronAddress(wallet.id) ?: ""
        val eventItems = historyHelper.tronMapping(
            wallet = wallet,
            events = events.map { it.copy() },
            tronAddress = tronAddress,
            options = ActionOptions(
                spamFilter = ActionOptions.SpamFilter.NOT_SPAM,
                safeMode = settingsRepository.isSafeModeEnabled(api),
                hiddenBalances = settingsRepository.hiddenBalances
            )
        )
        return eventItems
    }

    private suspend fun updateState() {
        _triggerFlow.emit(Unit)
    }

    private fun getTronLastLt(): Long? {
        val lt = _tronEventsFlow.value.lastOrNull { !it.inProgress }?.timestamp ?: return null
        if (0 >= lt) {
            return null
        }
        return lt
    }

    private fun hasPendingEvents(): Boolean {
        return _eventsFlow.value?.firstOrNull { it.inProgress } != null
    }

    private suspend fun loadDefault(beforeLt: Long?, limit: Int): List<AccountEventWrap> {
        val response = eventsRepository.getRemote(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            beforeLt = beforeLt,
            limit = limit,
        ) ?: throw IllegalStateException("Failed to load events")

        nextFrom = if (response.events.size == limit) {
            response.nextFrom
        } else {
            null
        }

        return response.events.map(::AccountEventWrap)
    }

    private suspend fun cache(): List<AccountEventWrap> {
        val list = eventsRepository.getLocal(
            accountId = wallet.accountId, testnet = wallet.testnet
        )?.events?.map { AccountEventWrap.cached(it) }
        return list ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private companion object {
        private const val TX_FILTER_NONE = 0
        private const val TX_FILTER_SENT = 1
        private const val TX_FILTER_RECEIVED = 2
        private const val TX_FILTER_APP = 3
        private const val TX_FILTER_DAPPS = 4
    }
}
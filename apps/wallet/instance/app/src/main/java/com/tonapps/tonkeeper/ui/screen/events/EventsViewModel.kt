package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.tonkeeper.core.history.ActionOutStatus
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.events.filters.FilterItem
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

// TODO: Refactor this class
class EventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountsRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
    private val dAppsRepository: DAppsRepository,
): BaseWalletVM(app) {

    private var autoRefreshJob: Job? = null

    private val _triggerFlow = MutableEffectFlow<Unit>()
    private val _loadingTriggerFlow = MutableEffectFlow<Unit>()

    private val _selectedFilter = MutableStateFlow<FilterItem?>(null)
    private val selectedFilter = _selectedFilter.asStateFlow()

    private val _filterAppsFlow = MutableStateFlow<List<AppEntity>>(emptyList())
    private val filterAppsFlow = _filterAppsFlow.asStateFlow()

    val uiFilterItemsFlow: Flow<List<FilterItem>> = combine(
        selectedFilter,
        filterAppsFlow,
    ) { selected, apps ->
        val uiFilterItems = mutableListOf<FilterItem>()
        apps.forEach { app ->
            uiFilterItems.add(FilterItem.App(selected?.id == app.id, app))
        }
        uiFilterItems.add(FilterItem.Send(selected?.type == FilterItem.TYPE_SEND))
        uiFilterItems.add(FilterItem.Receive(selected?.type == FilterItem.TYPE_RECEIVE))
        uiFilterItems.toList()
    }

    private val isLoading: AtomicBoolean = AtomicBoolean(false)

    private val _eventsFlow = MutableStateFlow<Array<AccountEventWrap>?>(null)
    private val _pushesFlow = MutableStateFlow<Array<AppPushEntity>?>(null)

    @OptIn(FlowPreview::class)
    private val eventsFlow = _eventsFlow.asStateFlow().filterNotNull().debounce(160)

    @OptIn(FlowPreview::class)
    private val pushesFlow = _pushesFlow.asStateFlow().filterNotNull().debounce(160)

    private val historyItemsFlow = combine(
        eventsFlow,
        pushesFlow,
        _triggerFlow,
    ) { events, pushes, _ ->
        mapping(events.map { it.event }, pushes.toList())
    }

    private val uiItemsFlow = combine(
        historyItemsFlow,
        selectedFilter,
        _loadingTriggerFlow
    ) { historyItems, filter, _ ->
        val actionOutStatus = resolveActionOutStatus(resolveFilter(filter))
        val uiItems = historyHelper.groupByDate(historyItems.filter {
            when (actionOutStatus) {
                ActionOutStatus.App -> it is HistoryItem.App
                ActionOutStatus.Send -> it is HistoryItem.Event && (it.actionOutStatus == ActionOutStatus.Send || it.actionOutStatus == ActionOutStatus.Any)
                ActionOutStatus.Received -> it is HistoryItem.Event && (it.actionOutStatus == ActionOutStatus.Received || it.actionOutStatus == ActionOutStatus.Any)
                else -> true
            }
        })
        val list = if (isLoading.get()) {
            historyHelper.withLoadingItem(uiItems)
        } else {
            historyHelper.removeLoadingItem(uiItems)
        }
        setCached(list)
        list
    }.flowOn(Dispatchers.IO)

    val uiStateFlow: Flow<EventsUiState> = flow {
        val cached = getCached()
        if (cached.isNotEmpty()) {
            emit(EventsUiState(
                uiItems = cached,
                loading = true
            ))
        }

        emitAll(uiItemsFlow.map {
            EventsUiState(it, isLoading.get())
        })
    }

    init {
        _triggerFlow.tryEmit(Unit)

        with(settingsRepository) {
            tokenPrefsChangedFlow.drop(1).collectFlow { initialLoad() }
            walletPrefsChangedFlow.drop(1).collectFlow { initialLoad() }
            safeModeStateFlow.drop(1).collectFlow { initialLoad() }
            hiddenBalancesFlow.drop(1).collectFlow { updateState() }
        }
        transactionManager.eventsFlow(wallet).drop(1).collectFlow { initialLoad() }
        eventsRepository.decryptedCommentFlow.collectFlow { updateState() }
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                checkAutoRefresh()
                delay(35.seconds)
            }
        }

        initialLoad()
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
                else -> TX_FILTER_APP
            }
        }
    }

    private fun resolveActionOutStatus(filter: Int): ActionOutStatus? {
        return when (filter) {
            TX_FILTER_APP -> ActionOutStatus.App
            TX_FILTER_SENT -> ActionOutStatus.Send
            TX_FILTER_RECEIVED -> ActionOutStatus.Received
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

    private suspend fun getDAppEvents(): List<AppPushEntity> {
        if (!wallet.isTonConnectSupported) {
            return emptyList()
        }

        val tonProof = accountsRepository.requestTonProofToken(wallet) ?: return emptyList()
        return dAppsRepository.getPushes(tonProof, wallet.accountId)
    }

    private fun checkAutoRefresh() {
        if (hasPendingEvents()) {
            initialLoad()
        }
    }

    fun initialLoad() {
        Log.d("EventsViewModelLog", "initialLoad")
        if (!isLoading.get()) {
            Log.d("EventsViewModelLog", "initialLoad: start")
            setLoading(loading = true, trigger = true)
            viewModelScope.launch(Dispatchers.IO) {
                val eventsDeferred = async { loadDefault(beforeLt = null).toTypedArray() }
                val dAppNotificationsDeferred = async { getDAppEvents().toTypedArray() }

                Log.d("EventsViewModelLog", "initialLoad: done")
                setLoading(loading = false, trigger = false)
                _pushesFlow.value = dAppNotificationsDeferred.await()
                _eventsFlow.value = eventsDeferred.await()
                Log.d("EventsViewModelLog", "initialLoad: done2")
            }
        } else {
            Log.d("EventsViewModelLog", "initialLoad: stop")
        }
    }

    fun loadMore() {
        if (isLoading.get()) {
            return
        }

        val lastLt = getLastLt() ?: return
        setLoading(loading = true, trigger = true)
        viewModelScope.launch(Dispatchers.IO) {
            val currentEvents = (_eventsFlow.value?.toMutableList() ?: mutableListOf())
            val beforeLtEvents = loadDefault(beforeLt = lastLt)
            val events = (currentEvents + beforeLtEvents).distinctBy { it.eventId }.sortedBy {
                it.timestamp
            }.reversed()
            setLoading(loading = false, trigger = false)
            _eventsFlow.value = events.toTypedArray()
        }
    }

    private suspend fun mapping(events: List<AccountEvent>, pushes: List<AppPushEntity>): List<HistoryItem> {
        val pushesItems = pushes.map {
            HistoryItem.App(context, wallet, it)
        }

        val eventItems = historyHelper.mapping(
            wallet = wallet,
            events = events.map { it.copy() },
            removeDate = false,
            hiddenBalances = settingsRepository.hiddenBalances,
            safeMode = settingsRepository.isSafeModeEnabled(),
        )

        return eventItems + pushesItems
    }

    private fun getCached(): List<HistoryItem> {
        return screenCacheSource.get(CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
    }

    private fun setCached(uiItems: List<HistoryItem>) {
        screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
    }

    private suspend fun updateState() {
        _triggerFlow.emit(Unit)
    }

    private fun getLastLt(): Long? {
        val lt = _eventsFlow.value?.lastOrNull { !it.inProgress }?.lt ?: return null
        if (0 >= lt) {
            return null
        }
        return lt
    }

    private fun hasPendingEvents(): Boolean {
        return _eventsFlow.value?.firstOrNull { it.inProgress } != null
    }

    private suspend fun loadDefault(beforeLt: Long?): List<AccountEventWrap> {
        val list = eventsRepository.getRemote(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            beforeLt = beforeLt,
            limit = 25,
        )?.events?.map(::AccountEventWrap)
        return list ?: emptyList()
    }

    private suspend fun cache(): List<AccountEventWrap> {
        val list = eventsRepository.getLocal(
            accountId = wallet.accountId,
            testnet = wallet.testnet
        )?.events?.map { AccountEventWrap.cached(it)}
        return list ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private companion object {
        private const val CACHE_NAME = "events"

        private const val TX_FILTER_NONE = 0
        private const val TX_FILTER_SENT = 1
        private const val TX_FILTER_RECEIVED = 2
        private const val TX_FILTER_APP = 3
    }
}
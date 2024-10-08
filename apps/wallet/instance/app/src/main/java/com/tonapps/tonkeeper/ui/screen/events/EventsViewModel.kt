package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.tonkeeper.core.entities.AssetsExtendedEntity
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

// TODO: Refactor this class
class EventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
): BaseWalletVM(app) {

    private var autoRefreshJob: Job? = null
    private var events: Array<AccountEventWrap>? = null
    private val isLoading: AtomicBoolean = AtomicBoolean(true)

    private val _uiStateFlow = MutableStateFlow(EventsUiState())
    val uiStateFlow = _uiStateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, EventsUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            setUiItems(getCached())
            submitEvents(cache(), true)
            submitEvents(load(), false)
        }

        eventsRepository.decryptedCommentFlow.collectFlow {
            updateState()
        }

        settingsRepository.hiddenBalancesFlow.drop(1).collectFlow {
            updateState()
        }

        transactionManager.eventsFlow(wallet).collectFlow { event ->
            /*if (event.pending) {
                appendEvent(AccountEventWrap(event.body))
            }*/
            refresh()
        }

        autoRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                checkAutoRefresh()
                delay(35.seconds)
            }
        }
    }

    private suspend fun checkAutoRefresh() {
        if (hasPendingEvents()) {
            setLoading()
            requestRefresh()
        }
    }

    fun refresh() {
        if (isLoading.get()) {
            return
        }
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            requestRefresh()
        }
    }

    private suspend fun requestRefresh() = withContext(Dispatchers.IO) {
        submitEvents(load(), false)
    }

    fun loadMore() {
        if (isLoading.get()) {
            return
        }

        val lastLt = getLastLt() ?: return

        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            val events = load(lastLt)
            appendEvents(events)
        }
    }

    private fun setLoading() {
        isLoading.set(true)

        _uiStateFlow.update {
            it.copy(
                items = historyHelper.withLoadingItem(it.items),
                isLoading = true
            )
        }
    }

    private fun setUiItems(uiItems: List<HistoryItem>) {
        val loading = isLoading.get()
        _uiStateFlow.value = EventsUiState(
            items = if (loading && uiItems.isNotEmpty()) {
                historyHelper.withLoadingItem(uiItems)
            } else {
                uiItems
            },
            isLoading = loading
        )
    }

    private suspend fun mapping(events: List<AccountEvent>): List<HistoryItem> {
        val items = historyHelper.mapping(
            wallet = wallet,
            events = events.map { it.copy() },
            removeDate = false,
            hiddenBalances = settingsRepository.hiddenBalances
        )
        return historyHelper.groupByDate(items)
    }

    private fun getCached(): List<HistoryItem> {
        return screenCacheSource.get(CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
    }

    private suspend fun updateState() = withContext(Dispatchers.IO) {
        val events = getEvents()
        val uiItems = mapping(events.map { it.event })
        setUiItems(uiItems)
        screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
    }

    private suspend fun submitEvents(newEvents: List<AccountEventWrap>, loading: Boolean) = withContext(Dispatchers.IO) {
        events = newEvents.distinctBy { it.eventId }
            .sortedByDescending { it.timestamp }
            .toTypedArray()
        isLoading.set(loading)
        updateState()
    }

    private suspend fun getEvents(): MutableList<AccountEventWrap> = withContext(Dispatchers.IO) {
        events?.map { it.copy() }?.toMutableList() ?: mutableListOf()
    }

    private suspend fun appendEvents(newEvents: List<AccountEventWrap>) {
        val list = getEvents() + newEvents.map { it.copy() }
        submitEvents(list, false)
    }

    private suspend fun appendEvent(event: AccountEventWrap) {
        appendEvents(listOf(event.copy()))
    }

    private fun getLastLt(): Long? {
        val lt = events?.lastOrNull { !it.inProgress }?.lt ?: return null
        if (0 >= lt) {
            return null
        }
        return lt
    }

    private fun hasPendingEvents(): Boolean {
        return events?.firstOrNull { it.inProgress } != null
    }

    private suspend fun load(beforeLt: Long? = null): List<AccountEventWrap> {
        val list = eventsRepository.getRemote(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            beforeLt = beforeLt
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
    }
}
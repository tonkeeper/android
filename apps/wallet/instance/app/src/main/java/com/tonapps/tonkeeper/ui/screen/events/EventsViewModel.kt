package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.AccountEventWrap
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class EventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
): BaseWalletVM(app) {

    private var accountEvents: List<AccountEventWrap>? = null

    private val _uiItemsFlow = MutableStateFlow<List<HistoryItem>?>(null)
    val uiItemsFlow = _uiItemsFlow.filterNotNull().shareIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val _isUpdatingFlow = MutableStateFlow(true)
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = getCached() ?: return@launch
            _uiItemsFlow.value = cached
        }

        eventsRepository.decryptedCommentFlow.onEach {
            submitAccountEvents(emptyList())
        }.launchIn(viewModelScope)

        collectFlow(settingsRepository.hiddenBalancesFlow.drop(1)) {
            val uiItems = _uiItemsFlow.value?.map {
                if (it is HistoryItem.Event) {
                    it.copy(hiddenBalance = settingsRepository.hiddenBalances)
                } else {
                    it
                }
            } ?: return@collectFlow

            _uiItemsFlow.value = uiItems.toList()
            screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
        }

        collectFlow(transactionManager.getEventsFlow(wallet).map {
            AccountEventWrap(
                event = it.body,
                cached = false,
                eventIds = it.eventIds
            )
        }) { accountEvent ->
            submitAccountEvents(listOf(accountEvent))
            if (!accountEvent.inProgress) {
                refresh()
            }
        }

        collectFlow(eventsRepository.getFlow(wallet.accountId, wallet.testnet)) { eventsResult ->
            submitAccountEvents(eventsResult.events.events.map { accountEvent ->
                AccountEventWrap(
                    event = accountEvent,
                    cached = eventsResult.cache,
                    eventIds = listOf(accountEvent.eventId)
                )
            })

            _isUpdatingFlow.value = eventsResult.cache
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        _isUpdatingFlow.value = true
        val events = eventsRepository.getRemote(wallet.accountId, wallet.testnet)?.events
        if (events != null) {
            submitAccountEvents(events.map { accountEvent ->
                AccountEventWrap(
                    event = accountEvent,
                    cached = false,
                    eventIds = listOf(accountEvent.eventId)
                )
            }, true)
        }
        _isUpdatingFlow.value = false
    }

    private suspend fun submitAccountEvents(
        newEvents: List<AccountEventWrap>,
        clear: Boolean = false,
    ) = withContext(Dispatchers.IO) {

        val currentEvents = if (clear) {
            mutableListOf()
        } else {
            accountEvents?.toMutableList() ?: mutableListOf()
        }

        for (newEvent in newEvents) {
            currentEvents.removeIf {
                newEvent.eventIds.contains(it.eventId) && it.cached && !newEvent.cached
            }
            currentEvents.add(newEvent)
        }

        val events = currentEvents.map {
            it.copy()
        }.distinctBy {
            it.eventId
        }.sortedByDescending {
            it.timestamp
        }

        accountEvents = events.toList()

        createUiItems(events.map { it.event })
    }

    private suspend fun createUiItems(events: List<AccountEvent>) {
        val uiItems = mapping(events)
        _uiItemsFlow.value = uiItems
        screenCacheSource.set(CACHE_NAME, wallet.id, uiItems.toList())
    }

    private fun lastLt(): Long? {
        val lt = accountEvents?.last()?.lt ?: return null
        if (lt > 0) {
            return lt
        }
        return null
    }

    fun loadMore() {
        if (_isUpdatingFlow.value) {
            return
        }
        val lastLt = lastLt() ?: return
        viewModelScope.launch {
            uiItemsLoading(true)
            val events = eventsRepository.getRemote(wallet.accountId, wallet.testnet, lastLt)?.events
            if (events == null) {
                uiItemsLoading(false)
                return@launch
            }
            submitAccountEvents(events.map {
                AccountEventWrap(
                    event = it,
                    cached = false,
                    eventIds = listOf(it.eventId)
                )
            })

            uiItemsLoading(false)
        }
    }

    private fun uiItemsLoading(loading: Boolean) {
        val oldValues = _uiItemsFlow.value ?: emptyList()
        _uiItemsFlow.value = if (loading) {
            historyHelper.withLoadingItem(oldValues)
        } else {
            historyHelper.removeLoadingItem(oldValues)
        }
        _isUpdatingFlow.value = loading
    }

    private suspend fun mapping(
        events: List<AccountEvent>
    ): List<HistoryItem> {
        val items = historyHelper.mapping(
            wallet = wallet,
            events = events.map { it.copy() },
            removeDate = false,
            hiddenBalances = settingsRepository.hiddenBalances
        )
        return historyHelper.groupByDate(items)
    }

    private fun getCached(): List<HistoryItem>? {
        val items: List<HistoryItem> = screenCacheSource.get(CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
        if (items.isEmpty()) {
            return null
        }
        return items
    }

    private companion object {
        private const val CACHE_NAME = "events"
    }
}
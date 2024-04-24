package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.helper.DateFormat
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow
import java.util.Calendar
import java.util.Date

class EventsViewModel(
    private val application: Application,
    private val walletRepository: WalletRepository,
    private val eventsRepository: EventsRepository,
    private val networkMonitor: NetworkMonitor,
    private val tonConnectRepository: TonConnectRepository,
    private val pushManager: PushManager,
    private val historyHelper: HistoryHelper,
): AndroidViewModel(application) {

    private val _isUpdatingFlow = MutableStateFlow(false)
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<HistoryItem>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        combine(
            walletRepository.activeWalletFlow,
            networkMonitor.isOnlineFlow
        ) { wallet, isOnline ->
            loadEvents(wallet, isOnline)
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)

        collectFlow(walletRepository.realtimeEventsFlow.map { it.wallet }) { wallet ->
            loadEvents(wallet, true)
        }

        collectFlow(pushManager.dAppPushFlow) {
            val wallet = walletRepository.activeWalletFlow.firstOrNull() ?: return@collectFlow
            loadEvents(wallet, true)
        }
    }

    fun openQRCode() = walletRepository.activeWalletFlow.take(1)

    private suspend fun getRemoteDAppEvents(wallet: WalletEntity): List<HistoryItem.App> {
        val events = pushManager.getRemoteDAppEvents(wallet)
        return dAppEventsMapping(events)
    }

    private suspend fun getLocalDAppEvents(wallet: WalletEntity): List<HistoryItem.App> {
        val events = pushManager.getLocalDAppEvents(wallet)
        return dAppEventsMapping(events)
    }

    private fun dAppEventsMapping(
        events: List<AppPushEntity>
    ): List<HistoryItem.App> {
        val dappUrls = events.map { it.dappUrl }
        val apps = tonConnectRepository.getApps(dappUrls)

        val items = mutableListOf<HistoryItem.App>()
        for (event in events) {
            val app = apps.firstOrNull {
                it.url.startsWith(event.dappUrl) && it.accountId == event.account
            } ?: continue

            items.add(HistoryItem.App(
                iconUri = Uri.parse(app.manifest.iconUrl),
                title = app.manifest.name,
                body = event.message,
                date = DateHelper.formatTime(event.dateUnix),
                timestamp = event.dateUnix,
                deepLink = event.link,
                host = app.manifest.host
            ))
        }
        return items
    }


    private fun getString(resId: Int): String {
        return application.getString(resId)
    }

    private fun foundLastItem(): HistoryItem.Event? {
        return _uiItemsFlow.value?.lastOrNull { it is HistoryItem.Event } as? HistoryItem.Event
    }

    private fun lastLt(): Long? {
        val item = foundLastItem() ?: return null
        if (item.lt > 0) {
            return item.lt
        }
        return null
    }

    fun loadMore() {
        if (_isUpdatingFlow.value) {
            return
        }
        val lastLt = lastLt() ?: return
        withUpdating {
            val wallet = walletRepository.activeWalletFlow.firstOrNull() ?: return@withUpdating
            val oldValues = _uiItemsFlow.value ?: emptyList()
            _uiItemsFlow.value = historyHelper.withLoadingItem(oldValues)
            loadRemote(wallet, lastLt)
        }
    }

    private fun loadEvents(
        wallet: WalletEntity,
        isOnline: Boolean
    ) {
        withUpdating {
            loadLocal(wallet)
            if (isOnline) {
                loadRemote(wallet)
            }
        }
    }

    private suspend fun loadLocal(wallet: WalletEntity) {
        val accountEvents = eventsRepository.getLocal(wallet.accountId, wallet.testnet) ?: return
        val walletEventItems = mapping(wallet, accountEvents.events)
        if (walletEventItems.isNotEmpty()) {
            setItems(walletEventItems + getLocalDAppEvents(wallet))
        }
    }

    private suspend fun loadRemote(wallet: WalletEntity, beforeLt: Long? = null) {
        val accountEvents = eventsRepository.getRemote(wallet.accountId, wallet.testnet, beforeLt) ?: return
        val walletEventItems = mapping(wallet, accountEvents.events)
        if (beforeLt == null) {
            setItems(walletEventItems + getRemoteDAppEvents(wallet))
        } else {
            val oldValues = _uiItemsFlow.value ?: emptyList()
            setItems(oldValues + walletEventItems)
        }
    }

    private suspend fun mapping(
        wallet: WalletEntity,
        events: List<AccountEvent>
    ): List<HistoryItem> {
        return historyHelper.mapping(
            wallet = wallet,
            events = events,
            removeDate = false
        )
    }

    private fun setItems(items: List<HistoryItem>) {
        val preparedItems = items.filter { it is HistoryItem.Event || it is HistoryItem.App }
            .sortedBy { it.timestampForSort }
            .reversed()

        val uiItems = mutableListOf<HistoryItem>()
        var currentDate: String? = null

        for (item in preparedItems) {
            val timestamp = item.timestampForSort
            val dateFormat = formatDate(timestamp)
            if (dateFormat != currentDate) {
                uiItems.add(HistoryItem.Header(dateFormat, item.timestampForSort))
                currentDate = dateFormat
            }
            uiItems.add(item)
        }

        _uiItemsFlow.value = uiItems.toList()
    }

    private fun formatDate(timestamp: Long): String {
        if (DateHelper.isToday(timestamp)) {
            return getString(Localization.today)
        } else if (DateHelper.isYesterday(timestamp)) {
            return getString(Localization.yesterday)
        } else if (DateHelper.isThisMonth(timestamp)) {
            return DateHelper.formatWeekDay(timestamp)
        } else if (DateHelper.isThisYear(timestamp)) {
            return DateHelper.formatMonth(timestamp)
        }
        return DateHelper.formatYear(timestamp)
    }

    private fun setUpdating(updating: Boolean) {
        _isUpdatingFlow.tryEmit(updating)
    }

    private fun withUpdating(action: suspend () -> Unit) {
        viewModelScope.launch {
            setUpdating(true)
            action()
            setUpdating(false)
        }
    }
}
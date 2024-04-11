package com.tonapps.tonkeeper.ui.screen.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.withMinus
import com.tonapps.extensions.withPlus
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.events.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.entities.ActionEntity
import com.tonapps.wallet.data.events.entities.EventEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class EventsViewModel(
    private val walletRepository: WalletRepository,
    private val eventsRepository: EventsRepository,
    private val networkMonitor: NetworkMonitor,
    private val api: API,
): ViewModel() {

    private data class Data(
        val wallet: WalletEntity,
        val list: List<HistoryItem>
    ) {

        fun lastOrNull() = list.lastOrNull()
    }

    private val _dataFlow = MutableStateFlow<Data?>(null)
    private val dataFlow = _dataFlow.asStateFlow().filterNotNull()

    private val _isUpdatingFlow = MutableStateFlow(false)
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        combine(walletRepository.activeWalletFlow, networkMonitor.isOnlineFlow) { wallet, isOnline ->
            loadEvents(wallet, isOnline)
        }.launchIn(viewModelScope)

        // collectFlow(walletRepository.realtimeEventsFlow.map { it.wallet }, ::loadRemote)
    }

    fun loadMore() {
        if (_isUpdatingFlow.value) {
            return
        }
        /*val data = _dataFlow.value ?: return
        val beforeLt = data.lastOrNull()?.lt ?: return

        _isUpdatingFlow.value = true

        viewModelScope.launch {
            try {
                val e = eventsRepository.getRemoteOffset(data.wallet.accountId, data.wallet.testnet, beforeLt)
                _dataFlow.value = Data(data.wallet, data.list + e)
                _isUpdatingFlow.value = false
            } catch (ignored: Throwable) {}
        }*/
    }

    private suspend fun loadEvents(
        wallet: WalletEntity,
        isOnline: Boolean
    ) = withContext(Dispatchers.IO) {
        _isUpdatingFlow.tryEmit(true)

        val list = api.getEvents(wallet.accountId, wallet.testnet, null, 20)
        val items = HistoryHelper.mapping(wallet, list)
        _uiItemsFlow.value += items
        _isUpdatingFlow.tryEmit(false)

        /*loadLocal(wallet)
        if (isOnline) {
            loadRemote(wallet)
        }*/

        /*
        val events = api.getEvents(accountId, testnet, beforeLt, limit).map { EventEntity(it, testnet) }.filter {
            it.actions.isNotEmpty()
        }
         */
    }

    /*private suspend fun loadLocal(wallet: WalletEntity) {
        _dataFlow.value = Data(wallet, eventsRepository.getLocal(wallet.accountId, wallet.testnet))
    }

    private suspend fun loadRemote(wallet: WalletEntity) {
        try {
            _dataFlow.value = Data(wallet, eventsRepository.getRemote(wallet.accountId, wallet.testnet))

        } catch (ignored: Throwable) { }
    }*/

    /*private fun map(wallet: WalletEntity, list: List<EventEntity>): List<Item> {
        val items = mutableListOf<Item>()

        for (event in list) {
            items.addAll(actions(wallet, event))
        }

        return items.toList()
    }

    private fun actions(
        wallet: WalletEntity,
        event: EventEntity,
    ): List<Item> {
        val items = mutableListOf<Item>()
        for ((index, action) in event.actions.withIndex()) {
            val position = ListCell.getPosition(event.actions.size, index)
            if (action.type == ActionType.TonTransfer) {
                items.addAll(actionTonTransfer(wallet, position, event, action))
            } else if (action.type == ActionType.JettonTransfer) {
                items.addAll(actionJettonTransfer(wallet, position, event, action))
            } else if (action.type == ActionType.NftTransfer) {
                items.addAll(actionNftTransfer(wallet, position, event, action))
            } else {
                items.add(Item.UnknownAction(position))
            }
            items.add(Item.Space)
        }
        return items
    }

    private fun actionTonTransfer(
        wallet: WalletEntity,
        position: ListCell.Position,
        event: EventEntity,
        action: ActionEntity,
    ): List<Item> {
        val sender = action.sender ?: return listOf(Item.UnknownAction(position))
        val recipient = action.recipient ?: return listOf(Item.UnknownAction(position))
        val token = action.token ?: return listOf(Item.UnknownAction(position))
        val amount = action.amount ?: return listOf(Item.UnknownAction(position))

        val items = mutableListOf<Item>()
        if (wallet.accountId == sender.accountId) {
            items.add(Item.SendAction(
                position = position,
                account = recipient,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withPlus
            ))
        } else {
            items.add(Item.ReceiveAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withPlus
            ))
        }
        return items
    }

    private fun actionJettonTransfer(
        wallet: WalletEntity,
        position: ListCell.Position,
        event: EventEntity,
        action: ActionEntity
    ): List<Item> {
        val sender = action.sender ?: return listOf(Item.UnknownAction(position))
        val recipient = action.recipient ?: return listOf(Item.UnknownAction(position))
        val token = action.token ?: return listOf(Item.UnknownAction(position))
        val amount = action.amount ?: return listOf(Item.UnknownAction(position))

        val items = mutableListOf<Item>()

        if (wallet.accountId == sender.accountId) {
            items.add(Item.SendAction(
                position = position,
                account = recipient,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withPlus
            ))
        } else {
            items.add(Item.ReceiveAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withPlus
            ))
        }


        return items
    }


    private fun actionNftTransfer(
        wallet: WalletEntity,
        position: ListCell.Position,
        event: EventEntity,
        action: ActionEntity
    ): List<Item> {
        val sender = action.sender ?: return listOf(Item.UnknownAction(position))
        val recipient = action.recipient ?: return listOf(Item.UnknownAction(position))

        val items = mutableListOf<Item>()

        if (sender.accountId == wallet.accountId && recipient.accountId != wallet.accountId) {
            items.add(Item.SendAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = "NFT",
                nft = action.nftEntity
            ))
        } else {
            items.add(Item.ReceiveAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = "NFT",
                nft = action.nftEntity
            ))
        }
        return items
    } */
}
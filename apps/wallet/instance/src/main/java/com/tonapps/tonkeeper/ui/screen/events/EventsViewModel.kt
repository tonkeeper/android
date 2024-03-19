package com.tonapps.tonkeeper.ui.screen.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.withMinus
import com.tonapps.extensions.withPlus
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.ui.screen.events.list.Item
import com.tonapps.uikit.list.ListCell
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class EventsViewModel(
    private val walletRepository: WalletRepository,
    private val eventsRepository: EventsRepository,
    private val networkMonitor: NetworkMonitor
): ViewModel() {

    private val _isUpdatingFlow = MutableEffectFlow<Boolean>()
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        combine(walletRepository.activeWalletFlow, networkMonitor.isOnlineFlow) { wallet, isOnline ->
            loadEvents(wallet, isOnline)
        }.launchIn(viewModelScope)

        collectFlow(walletRepository.realtimeEventsFlow.map { it.wallet }, ::loadRemote)
    }

    private suspend fun loadEvents(
        wallet: WalletEntity,
        isOnline: Boolean
    ) = withContext(Dispatchers.IO) {
        _isUpdatingFlow.tryEmit(true)
        loadLocal(wallet)
        if (isOnline) {
            loadRemote(wallet)
        }
    }

    private suspend fun loadLocal(wallet: WalletEntity) {
        val events = eventsRepository.getLocal(wallet.accountId, wallet.testnet)
        _uiItemsFlow.value = map(wallet, events)
    }

    private suspend fun loadRemote(wallet: WalletEntity) {
        try {
            val events = eventsRepository.getRemote(wallet.accountId, wallet.testnet)
            _uiItemsFlow.value = map(wallet, events)
            _isUpdatingFlow.tryEmit(false)
        } catch (ignored: Throwable) { }
    }

    private fun map(wallet: WalletEntity, list: List<EventEntity>): List<Item> {
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

        if (sender.accountId == wallet.accountId && recipient.accountId != wallet.accountId) {
            items.add(Item.SendAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withMinus
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

        if (sender.accountId == wallet.accountId && recipient.accountId != wallet.accountId) {
            items.add(Item.SendAction(
                position = position,
                account = sender,
                comment = action.comment,
                loading = event.inProgress,
                value = CurrencyFormatter.format(token.symbol, amount).withMinus
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
    }
}
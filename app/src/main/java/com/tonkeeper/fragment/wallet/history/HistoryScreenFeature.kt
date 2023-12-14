package com.tonkeeper.fragment.wallet.history

import com.tonkeeper.App
import com.tonkeeper.api.event.EventRepository
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.event.WalletStateUpdateEvent
import core.EventBus
import core.QueueScope
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import ton.wallet.Wallet

class HistoryScreenFeature: UiFeature<HistoryScreenState, HistoryScreenEffect>(HistoryScreenState()) {

    private val newTransactionItem = fun(_: WalletStateUpdateEvent) {
        syncEvents()
    }

    private val queueScope = QueueScope(Dispatchers.IO)
    private val eventRepository = EventRepository()

    init {
        requestEventsState()
        syncEvents()

        EventBus.subscribe(WalletStateUpdateEvent::class.java, newTransactionItem)
    }

    private fun syncEvents() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            val wallet = getWallet() ?: return@submit

            eventRepository.clear(wallet.accountId)

            updateEventsState()
        }
    }

    private suspend fun updateEventsState() {
        val wallet = getWallet() ?: return
        val events = eventRepository.get(wallet.accountId)
        val items = HistoryHelper.mapping(wallet, events)

        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Default,
                items = items
            )
        }

        delay(100)

        sendEffect(HistoryScreenEffect.UpScroll)
    }

    private fun requestEventsState() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            updateEventsState()
        }
    }

    private suspend fun getWallet(): Wallet? {
        return App.walletManager.getWalletInfo()
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()

        EventBus.unsubscribe(WalletStateUpdateEvent::class.java, newTransactionItem)
    }

}
package com.tonkeeper.fragment.wallet.history

import com.tonkeeper.App
import com.tonkeeper.api.event.EventRepository
import com.tonkeeper.core.history.HistoryHelper
import core.QueueScope
import ton.wallet.WalletInfo
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.wallet.Wallet

class HistoryScreenFeature: UiFeature<HistoryScreenState, HistoryScreenEffect>(HistoryScreenState()) {

    private val queueScope = QueueScope(Dispatchers.IO)
    private val eventRepository = EventRepository()


    init {
        requestEventsState()
        syncEvents()
    }

    private fun syncEvents() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            val wallet = getWallet() ?: return@submit

            eventRepository.sync(wallet.address)

            updateEventsState()
        }
    }

    private suspend fun updateEventsState() {
        val wallet = getWallet() ?: return
        val events = eventRepository.get(wallet.address)
        val items = HistoryHelper.mapping(wallet, events)

        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Default,
                items = items
            )
        }
    }

    private fun requestEventsState() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        queueScope.submit {
            updateEventsState()
        }
    }

    private suspend fun getWallet(): Wallet? {
        return App.walletManager.getWalletInfo()
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }

}
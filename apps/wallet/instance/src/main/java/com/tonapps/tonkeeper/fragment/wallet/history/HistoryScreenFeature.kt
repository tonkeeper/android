package com.tonapps.tonkeeper.fragment.wallet.history

import com.tonapps.tonkeeper.api.history.HistoryRepository
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.event.WalletStateUpdateEvent
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import core.EventBus
import core.QueueScope
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import uikit.extensions.collectFlow

class HistoryScreenFeature(
    private val walletRepository: WalletRepository
): UiFeature<HistoryScreenState, HistoryScreenEffect>(HistoryScreenState()) {

    private val queueScope = QueueScope(Dispatchers.IO)
    private val historyRepository = HistoryRepository()

    init {
        collectFlow(walletRepository.activeWalletFlow) {
            requestEventsState()
        }
    }

    private fun requestEventsState() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading,
                items = emptyList()
            )
        }

        queueScope.submit {
            updateEventsState(false)
            updateEventsState(true)
        }
    }

    fun loadMore(lt: Long) {
        queueScope.submit(Dispatchers.IO) {
            updateUiState { currentState ->
                currentState.copy(
                    items = HistoryHelper.withLoadingItem(currentState.items)
                )
            }

            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@submit
            val events = historyRepository.getWithOffset(wallet.accountId, wallet.testnet, lt) ?: emptyList()
            val items = HistoryHelper.removeLoadingItem(uiState.value.items) + HistoryHelper.mapping(wallet, events)

            updateUiState { currentState ->
                currentState.copy(
                    items = items,
                    loadedAll = HistoryHelper.EVENT_LIMIT > events.size
                )
            }
        }
    }

    private suspend fun updateEventsState(sync: Boolean) {
        val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId
        val response = if (sync) {
            historyRepository.getFromCloud(accountId, wallet.testnet)
        } else {
            historyRepository.get(accountId, wallet.testnet)
        }

        val events = response?.data ?: return
        val items = HistoryHelper.mapping(wallet, events)

        val asyncState = if (sync) {
            AsyncState.Default
        } else {
            uiState.value.asyncState
        }

        updateUiState { currentState ->
            currentState.copy(
                asyncState = asyncState,
                items = items,
                loadedAll = HistoryHelper.EVENT_LIMIT > events.size
            )
        }

        delay(100)

        sendEffect(HistoryScreenEffect.UpScroll)
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }

}
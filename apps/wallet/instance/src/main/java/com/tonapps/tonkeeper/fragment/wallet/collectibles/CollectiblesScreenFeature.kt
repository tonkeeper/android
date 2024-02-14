package com.tonapps.tonkeeper.fragment.wallet.collectibles

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.collectibles.CollectiblesRepository
import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.CollectiblesItem
import kotlinx.coroutines.launch
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class CollectiblesScreenFeature: UiFeature<CollectiblesScreenState, CollectiblesScreenEffect>(
    CollectiblesScreenState()
) {

    private val repository = CollectiblesRepository()

    init {
        requestState()
    }

    private fun requestState() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        viewModelScope.launch {
            load(false)
            load(true)
        }
    }

    private suspend fun load(sync: Boolean) {
        val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId
        val response = if (sync) {
            repository.getFromCloud(accountId, wallet.testnet)
        } else {
            repository.get(accountId, wallet.testnet)
        }

        val nftItems = response?.data ?: return

        val items = mutableListOf<CollectiblesItem>()
        for (nftItem in nftItems) {
            items.add(CollectiblesItem(nftItem))
        }

        val asyncState = if (sync) {
            uiState.value.asyncState
        } else {
            AsyncState.Default
        }

        updateUiState {
            it.copy(
                asyncState = asyncState,
                items = items
            )
        }
    }
}
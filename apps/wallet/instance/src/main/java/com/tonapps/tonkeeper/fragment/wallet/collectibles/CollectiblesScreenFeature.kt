package com.tonapps.tonkeeper.fragment.wallet.collectibles

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.collectibles.CollectiblesRepository
import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.Item
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class CollectiblesScreenFeature(
    private val walletRepository: WalletRepository
): UiFeature<CollectiblesScreenState, CollectiblesScreenEffect>(
    CollectiblesScreenState()
) {

    private val repository = CollectiblesRepository()

    init {
        collectFlow(walletRepository.activeWalletFlow) {
            requestState(it)
        }
    }

    private fun requestState(wallet: WalletEntity) {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading,
                items = Item.Skeleton.list
            )
        }

        viewModelScope.launch {
            load(false, wallet)
            load(true, wallet)
        }
    }

    private suspend fun load(sync: Boolean, wallet: WalletEntity) {
        val accountId = wallet.accountId
        val response = if (sync) {
            repository.getFromCloud(accountId, wallet.testnet)
        } else {
            repository.get(accountId, wallet.testnet)
        }

        val nftItems = response?.data ?: return

        val items = mutableListOf<Item>()
        for (nftItem in nftItems) {
            items.add(Item.Nft(nftItem))
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
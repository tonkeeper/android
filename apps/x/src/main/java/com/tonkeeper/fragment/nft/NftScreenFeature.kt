package com.tonkeeper.fragment.nft

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.collectibles.CollectiblesRepository
import com.tonkeeper.api.nft.NftRepository
import io.tonapi.models.NftItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class NftScreenFeature: UiFeature<NftScreenState, NftScreenEffect>(NftScreenState()) {

    private val collectiblesRepository = CollectiblesRepository()
    private val nftRepository = NftRepository()

    fun load(nftAddress: String) {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val nftItem = getNft(nftAddress, wallet.testnet)

            if (nftItem == null) {
                sendEffect(NftScreenEffect.FailedLoad)
                return@launch
            }

            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Default,
                    nftItem = nftItem
                )
            }
        }
    }

    private suspend fun getNft(
        address: String,
        testnet: Boolean,
    ): NftItem? = withContext(Dispatchers.IO) {
        val nftItem = collectiblesRepository.getNftItemCache(address)
        nftItem ?: nftRepository.getItem(address, testnet)
    }
}
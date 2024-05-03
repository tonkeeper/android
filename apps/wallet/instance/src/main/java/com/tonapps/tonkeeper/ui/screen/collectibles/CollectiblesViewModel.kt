package com.tonapps.tonkeeper.ui.screen.collectibles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Item
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext

class CollectiblesViewModel(
    private val walletRepository: WalletRepository,
    private val repository: CollectiblesRepository,
    private val networkMonitor: NetworkMonitor
): ViewModel() {

    private val _isUpdatingFlow = MutableEffectFlow<Boolean>()
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        combine(walletRepository.activeWalletFlow, networkMonitor.isOnlineFlow) { wallet, isOnline ->
            loadItems(wallet, isOnline)
        }.launchIn(viewModelScope)
    }

    fun openQRCode() = walletRepository.activeWalletFlow.take(1)

    private suspend fun loadItems(
        wallet: WalletEntity,
        isOnline: Boolean
    ) = withContext(Dispatchers.IO) {
        _isUpdatingFlow.tryEmit(true)
        loadLocal(wallet)

        if (isOnline) {
            loadRemote(wallet)
        }
    }

    private fun loadLocal(wallet: WalletEntity) {
        val purchases = repository.getLocalNftItems(wallet.accountId, wallet.testnet)
        val items = buildUiItems(purchases)
        if (items.isNotEmpty()) {
            setUiItems(wallet, items)
        }
    }

    private fun loadRemote(wallet: WalletEntity) {
        try {
            val purchases = repository.getRemoteNftItems(wallet.accountId, wallet.testnet)
            val items = buildUiItems(purchases)
            setUiItems(wallet, items)
            _isUpdatingFlow.tryEmit(false)
        } catch (ignored: Throwable) { }
    }

    private fun buildUiItems(
        list: List<NftEntity>
    ): List<Item> {
        val items = mutableListOf<Item>()
        for (nft in list) {
            items.add(Item.Nft(nft))
        }
        return items.toList()
    }

    private fun setUiItems(
        wallet: WalletEntity,
        items: List<Item>
    ) {
        _uiItemsFlow.value = items.toList()
    }
}
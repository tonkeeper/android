package com.tonapps.tonkeeper.ui.screen.collectibles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
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
    private val accountRepository: AccountRepository,
    private val repository: CollectiblesRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository
): ViewModel() {

    private val _isUpdatingFlow = MutableEffectFlow<Boolean>()
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    val changeWalletFlow = accountRepository.selectedWalletFlow

    init {
        combine(
            accountRepository.selectedWalletFlow,
            networkMonitor.isOnlineFlow,
            settingsRepository.hiddenBalancesFlow,
            settingsRepository.nftPrefsChangedFlow
        ) { wallet, isOnline, hiddenBalances, _ ->
            loadItems(wallet, isOnline, hiddenBalances)
        }.launchIn(viewModelScope)
    }

    fun openQRCode() = accountRepository.selectedWalletFlow.take(1)

    private suspend fun loadItems(
        wallet: WalletEntity,
        isOnline: Boolean,
        hiddenBalances: Boolean
    ) = withContext(Dispatchers.IO) {
        _isUpdatingFlow.tryEmit(true)
        loadLocal(wallet, hiddenBalances)

        if (isOnline) {
            loadRemote(wallet, hiddenBalances)
        }
    }

    private suspend fun loadLocal(wallet: WalletEntity, hiddenBalances: Boolean) {
        val purchases = repository.getLocalNftItems(wallet.accountId, wallet.testnet)
        val items = buildUiItems(wallet, purchases, hiddenBalances)
        if (items.isNotEmpty()) {
            setUiItems(wallet, items)
        }
    }

    private suspend fun loadRemote(wallet: WalletEntity, hiddenBalances: Boolean) {
        try {
            val purchases = repository.getRemoteNftItems(wallet.accountId, wallet.testnet)
            val items = buildUiItems(wallet, purchases, hiddenBalances)
            setUiItems(wallet, items)
            _isUpdatingFlow.tryEmit(false)
        } catch (ignored: Throwable) { }
    }

    private suspend fun buildUiItems(
        wallet: WalletEntity,
        list: List<NftEntity>,
        hiddenBalances: Boolean
    ): List<Item> {
        val items = mutableListOf<Item>()
        for (nft in list) {
            val nftPref = settingsRepository.getNftPrefs(wallet.id, nft.address)
            if (nftPref.hidden) {
                continue
            }
            if (!nft.isTrusted && nftPref.trust) {
                items.add(Item.Nft(nft.copy(isTrusted = true), hiddenBalances))
            } else {
                items.add(Item.Nft(nft, hiddenBalances))
            }
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
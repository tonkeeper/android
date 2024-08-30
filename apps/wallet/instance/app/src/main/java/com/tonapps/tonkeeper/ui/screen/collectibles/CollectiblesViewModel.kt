package com.tonapps.tonkeeper.ui.screen.collectibles

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.flattenFirst
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.extensions.with
import com.tonapps.tonkeeper.ui.base.UiListState
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take

class CollectiblesViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    val uiListStateFlow = combine(
        accountRepository.selectedWalletFlow,
        networkMonitor.isOnlineFlow,
        settingsRepository.hiddenBalancesFlow,
        settingsRepository.tokenPrefsChangedFlow,
    ) { wallet, isOnline, hiddenBalances, _ ->
        stateFlow(
            wallet = wallet,
            hiddenBalances = hiddenBalances,
            isOnline = isOnline
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null).filterNotNull().flattenFirst()

    private fun stateFlow(
        wallet: WalletEntity,
        hiddenBalances: Boolean,
        isOnline: Boolean
    ): Flow<UiListState> = flow {
        emit(UiListState.Loading)
        emitAll(itemsFlow(wallet, hiddenBalances, isOnline))
    }

    private fun itemsFlow(
        wallet: WalletEntity,
        hiddenBalances: Boolean,
        isOnline: Boolean,
    ): Flow<UiListState> = collectiblesRepository.getFlow(wallet.address, wallet.testnet, isOnline).map { result ->
        val uiItems = mutableListOf<Item>()
        for (nft in result.list) {
            val isHiddenCollection = nft.collection?.address?.let {
                settingsRepository.getTokenPrefs(wallet.id, it).isHidden
            } ?: false

            if (isHiddenCollection) {
                continue
            }

            val nftPref = settingsRepository.getTokenPrefs(wallet.id, nft.address)
            if (nftPref.isHidden) {
                continue
            }
            uiItems.add(Item.Nft(nft.with(nftPref), hiddenBalances))
        }

        if (uiItems.isEmpty() && !result.cache) {
            UiListState.Empty
        } else if (uiItems.isEmpty()) {
            UiListState.Loading
        } else {
            UiListState.Items(result.cache, uiItems.toList())
        }
    }.flowOn(Dispatchers.IO)

    fun openQRCode() = accountRepository.selectedWalletFlow.take(1)

}
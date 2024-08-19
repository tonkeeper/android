package com.tonapps.tonkeeper.ui.screen.collectibles

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.flattenFirst
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.entity.Result
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext

class CollectiblesViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    private val resultFlow = combine(
        accountRepository.selectedWalletFlow,
        networkMonitor.isOnlineFlow,
        settingsRepository.hiddenBalancesFlow,
        settingsRepository.tokenPrefsChangedFlow,
    ) { wallet, isOnline, hiddenBalances, _ ->
        collectiblesFlow(
            wallet = wallet,
            hiddenBalances = hiddenBalances,
            isOnline = isOnline
        )
    }.flattenFirst()

    val uiItemsFlow = resultFlow.map { it.second }
    val uiUpdatingFlow = resultFlow.map { it.first }

    private fun collectiblesFlow(
        wallet: WalletEntity,
        hiddenBalances: Boolean,
        isOnline: Boolean,
    ) = collectiblesRepository.getFlow(wallet.address, wallet.testnet, isOnline).map { result ->

        val uiItems = mutableListOf<Item>()
        result.list?.let {
            for (nft in it) {
                val tokenPref = settingsRepository.getTokenPrefs(wallet.id, nft.address)
                if (tokenPref.isHidden) {
                    continue
                }
                if (!nft.isTrusted && tokenPref.isTrust) {
                    uiItems.add(Item.Nft(nft.copy(isTrusted = true), hiddenBalances))
                } else {
                    uiItems.add(Item.Nft(nft, hiddenBalances))
                }
            }
        }

        Pair(result.cache, uiItems.toList())
    }.flowOn(Dispatchers.IO)

    fun openQRCode() = accountRepository.selectedWalletFlow.take(1)

}
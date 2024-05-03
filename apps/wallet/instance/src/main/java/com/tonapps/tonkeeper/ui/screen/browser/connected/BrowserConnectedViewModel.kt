package com.tonapps.tonkeeper.ui.screen.browser.connected

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.mapList
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Item
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BrowserConnectedViewModel(
    private val walletRepository: WalletRepository,
    private val tonConnectRepository: TonConnectRepository
): ViewModel() {

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        combine(walletRepository.activeWalletFlow, tonConnectRepository.appsFlow) { wallet, apps ->
            apps.filter { it.accountId == wallet.accountId }.distinctBy { it.url }.map { Item(it) }
        }.onEach {
            _uiItemsFlow.value = it
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    fun deleteApp(app: DAppEntity) {
        tonConnectRepository.disconnect(app)

    }
}
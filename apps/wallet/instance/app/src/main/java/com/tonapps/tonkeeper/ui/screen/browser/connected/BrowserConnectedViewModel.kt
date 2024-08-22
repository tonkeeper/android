package com.tonapps.tonkeeper.ui.screen.browser.connected

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Item
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class BrowserConnectedViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val tonConnectRepository: TonConnectRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        combine(
            accountRepository.selectedWalletFlow,
            tonConnectRepository.connectionsFlow
        ) { wallet, apps ->
            apps.filter { it.accountId == wallet.accountId }.distinctBy { it.url }.map {
                Item(it)
            }
        }.onEach {
            _uiItemsFlow.value = it
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    fun deleteConnect(connect: DConnectEntity) {
        accountRepository.selectedWalletFlow.take(1).onEach {
            tonConnectRepository.disconnect(it, connect, settingsRepository.firebaseToken)
        }

    }
}
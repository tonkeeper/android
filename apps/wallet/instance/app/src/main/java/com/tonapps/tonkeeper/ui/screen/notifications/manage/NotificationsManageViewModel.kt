package com.tonapps.tonkeeper.ui.screen.notifications.manage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.notifications.manage.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class NotificationsManageViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectRepository: TonConnectRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        tonConnectRepository.connectionsFlow.take(1).map { apps ->
            val myApps = apps.filter { it.walletId == wallet.id }
            val uiItems = mutableListOf<Item>()
            uiItems.add(
                Item.Wallet(
                    pushEnabled = settingsRepository.getPushWallet(wallet.id),
                    walletId = wallet.id
                ))
            uiItems.add(Item.Space)
            if (myApps.isNotEmpty()) {
                uiItems.add(Item.AppsHeader)
                uiItems.add(Item.Space)
                for ((index, app) in myApps.withIndex()) {
                    val position = ListCell.getPosition(myApps.size, index)
                    uiItems.add(Item.App(app, position))
                }
                uiItems.add(Item.Space)
            }
            _uiItemsFlow.value = uiItems
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    fun enabledPush(url: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = GooglePushService.requestToken()
            tonConnectRepository.setPushEnabled(wallet, url, enabled, token)
        }
    }
}
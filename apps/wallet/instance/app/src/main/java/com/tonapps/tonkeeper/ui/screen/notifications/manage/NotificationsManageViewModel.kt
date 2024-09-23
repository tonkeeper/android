package com.tonapps.tonkeeper.ui.screen.notifications.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.notifications.manage.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationsManageViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectManager: TonConnectManager,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    val uiItemsFlow = tonConnectManager.walletAppsFlow(wallet).map { apps ->
        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Wallet(
            wallet = wallet,
            pushEnabled = settingsRepository.getPushWallet(wallet.id)
        ))
        uiItems.add(Item.Space)
        if (apps.isNotEmpty()) {
            uiItems.add(Item.AppsHeader)
            uiItems.add(Item.Space)
            for ((index, entity) in apps.withIndex()) {
                val position = ListCell.getPosition(apps.size, index)
                val pushEnabled = tonConnectManager.isPushEnabled(wallet, entity.host)
                uiItems.add(Item.App(
                    app = entity,
                    wallet = wallet,
                    pushEnabled = pushEnabled,
                    position = position
                ))
            }
            uiItems.add(Item.Space)
        }
        uiItems
    }.flowOn(Dispatchers.IO)

    fun enabledPush(host: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = GooglePushService.requestToken()
            // tonConnectRepository.setPushEnabled(wallet, url, enabled, token)
        }
    }
}
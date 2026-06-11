package com.tonapps.tonkeeper.ui.screen.settings.apps

import android.app.Application
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class AppsViewModel(
    application: Application,
    private val wallet: WalletEntity,
    private val tonConnectBridge: ITonConnectBridge
): BaseWalletVM(application) {

    val uiItemsFlow = tonConnectBridge.walletAppsFlow(wallet).map { apps ->
        val uiItems = mutableListOf<Item>()
        if (apps.isNotEmpty()) {
            uiItems.add(Item.DisconnectAll)
            for ((index, app) in apps.withIndex()) {
                val position = ListCell.getPosition(apps.size, index)
                uiItems.add(
                    Item.App(
                        app = app,
                        wallet = wallet,
                        position = position
                    )
                )
            }
        } else {
            uiItems.add(Item.Empty)
        }
        uiItems.toList()
    }

    fun disconnectApp(app: AppEntity) {
        tonConnectBridge.disconnect(wallet, app.url)
    }

    fun disconnectAll() {
        tonConnectBridge.walletAppsFlow(wallet).take(1).collectFlow { apps ->
            for (app in apps) {
                tonConnectBridge.disconnect(wallet, app.url)
            }
        }
    }
}
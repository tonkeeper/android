package com.tonapps.tonkeeper.ui.screen.browser.connected

import android.app.Application
import com.tonapps.extensions.mapList
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

class BrowserConnectedViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectManager: TonConnectManager
): BaseWalletVM(app) {

    val uiItemsFlow = tonConnectManager.walletAppsFlow(wallet).mapList {
        Item(wallet, it)
    }

    fun deleteConnect(app: AppEntity) {
        tonConnectManager.disconnect(wallet, app.url)
    }
}
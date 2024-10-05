package com.tonapps.tonkeeper.ui.screen.add.imprt

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.add.imprt.list.Item
import com.tonapps.wallet.api.API
import kotlinx.coroutines.flow.map

class ImportWalletViewModel(
    app: Application,
    private val api: API,
): BaseWalletVM(app) {

    val uiItems = api.configFlow.map {
        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.import)
        uiItems.add(Item.watch)
        uiItems.add(Item.testnet)
        if (!api.config.flags.disableSigner) {
            uiItems.add(Item.signer)
        }
        uiItems.add(Item.keystone)
        uiItems.add(Item.ledger)
        uiItems.toList()
    }
}
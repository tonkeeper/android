package com.tonapps.tonkeeper.ui.screen.add

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.map

class AddWalletViewModel(
    app: Application,
    private val withNew: Boolean,
    private val api: API,
): BaseWalletVM(app) {

    val uiItems = api.configFlow.map {
        val uiItems = mutableListOf<Item>()
        if (withNew) {
            uiItems.add(Item.header(Localization.add_wallet, Localization.add_wallet_description))
            uiItems.add(Item.new)
        } else {
            uiItems.add(Item.header(Localization.import_wallet, Localization.import_wallet_subtitle))
        }
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
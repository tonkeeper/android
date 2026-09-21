package com.tonapps.tonkeeper.ui.screen.add

import android.app.Application
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.core.flags.WalletFeature
import com.tonapps.tonkeeper.core.DevSettings
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
            val isMultichain = WalletFeature.Multichain.isEnabled && (it.flags.multichainEnabled || WalletFeature.Multichain.isOverridden)
            uiItems.add(
                when (isMultichain) {
                    true -> Item.newMultichain
                    false -> Item.new
                }
            )
        } else {
            uiItems.add(Item.header(Localization.import_wallet, Localization.import_wallet_subtitle))
        }

        val isImportMultichain = WalletFeature.ImportMultichainWallet.isEnabled && (it.flags.multichainEnabled || WalletFeature.ImportMultichainWallet.isOverridden)
        uiItems.add(
            when (isImportMultichain) {
                true -> Item.importMultichain
                false -> Item.import
            }
        )

        uiItems.add(Item.otherOptionsTitle)

        uiItems.add(Item.ledger)
        uiItems.add(Item.keystone)
        if (!api.getConfig(TonNetwork.MAINNET).flags.disableSigner) {
            uiItems.add(Item.signer)
        }
        uiItems.add(Item.watch)
        if (DevSettings.tetraEnabled) {
            uiItems.add(Item.forDevelopersTitle)
            uiItems.add(Item.tetra)
        }

        uiItems.toList()
    }
}
package com.tonapps.tonkeeper.ui.screen.tonconnect

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

sealed class TonConnectScreenState {

    data class Data(
        val wallet: WalletEntity,
        val hasWalletPicker: Boolean
    ): TonConnectScreenState()

    data object Failure: TonConnectScreenState()
}
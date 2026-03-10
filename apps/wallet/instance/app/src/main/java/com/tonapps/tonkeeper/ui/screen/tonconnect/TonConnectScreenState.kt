package com.tonapps.tonkeeper.ui.screen.tonconnect

import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

sealed class TonConnectScreenState {

    data class Data(
        val wallet: WalletEntity,
        val hasWalletPicker: Boolean
    ): TonConnectScreenState()

    data object Failure: TonConnectScreenState()
}
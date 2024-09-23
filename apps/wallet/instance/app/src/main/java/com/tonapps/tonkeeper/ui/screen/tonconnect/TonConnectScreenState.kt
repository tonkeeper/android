package com.tonapps.tonkeeper.ui.screen.tonconnect

import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

data class TonConnectScreenState(
    val wallet: WalletEntity,
    val hasWalletPicker: Boolean
)
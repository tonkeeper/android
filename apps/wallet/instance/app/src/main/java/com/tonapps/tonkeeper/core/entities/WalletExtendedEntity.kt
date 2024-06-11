package com.tonapps.tonkeeper.core.entities

import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.entities.WalletPrefsEntity

data class WalletExtendedEntity(
    val raw: WalletEntity,
    val prefs: WalletPrefsEntity,
) {

    val index: Int
        get() = prefs.index
}
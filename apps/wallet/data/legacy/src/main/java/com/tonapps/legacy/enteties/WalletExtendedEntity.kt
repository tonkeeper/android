package com.tonapps.legacy.enteties

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.settings.entities.WalletPrefsEntity

data class WalletExtendedEntity(
    val raw: WalletEntity,
    val prefs: WalletPrefsEntity,
) {

    val index: Int
        get() = prefs.index
}
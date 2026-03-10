package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity

fun NftEntity.with(pref: TokenPrefsEntity): NftEntity {
    if (pref.isTrust) {
        return copy(trust = Trust.whitelist)
    }
    return this
}

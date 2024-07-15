package com.tonapps.tonkeeper.core.entities

import android.net.Uri
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class TokenExtendedEntity(
    val raw: AccountTokenEntity,
    val prefs: TokenPrefsEntity,
) {

    companion object {
        val comparator = compareBy<TokenExtendedEntity> {
            !it.isTon
        }.thenBy {
            !it.pinned
        }.thenBy {
            it.index
        }
    }

    val imageUri: Uri
        get() = raw.imageUri

    val balance: BalanceEntity
        get() = raw.balance

    val fiat: Coins
        get() = raw.fiat

    val address: String
        get() = raw.address

    val symbol: String
        get() = raw.symbol

    val name: String
        get() = raw.name

    val rateNow: Coins
        get() = raw.rateNow

    val rateDiff24h: String
        get() = raw.rateDiff24h

    val verified: Boolean
        get() = raw.verified

    val pinned: Boolean
        get() = prefs.pinned

    val hidden: Boolean
        get() = prefs.hidden

    val index: Int
        get() = prefs.index

    val isTon: Boolean
        get() = raw.isTon
}
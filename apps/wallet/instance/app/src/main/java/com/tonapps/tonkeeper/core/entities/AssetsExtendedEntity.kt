package com.tonapps.tonkeeper.core.entities

import android.net.Uri
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class AssetsExtendedEntity(
    val raw: AssetsEntity,
    val prefs: TokenPrefsEntity,
) {

    companion object {
        val comparator = compareBy<AssetsExtendedEntity> {
            !it.isTon
        }.thenBy {
            !it.pinned
        }.thenBy {
            !it.verified
        }.thenBy {
            it.index
        }
    }

    private val token: AccountTokenEntity
        get() = when (raw) {
            is AssetsEntity.Token -> raw.token
            is AssetsEntity.Staked -> AccountTokenEntity(
                balance = raw.staked.balance,
            )
        }

    val imageUri: Uri
        get() = token.imageUri

    val balance: BalanceEntity
        get() = token.balance

    val fiat: Coins
        get() = raw.fiat

    val address: String
        get() = token.address

    val symbol: String
        get() = token.symbol

    val name: String
        get() = token.name

    val rateNow: Coins
        get() = token.rateNow

    val rateDiff24h: String
        get() = token.rateDiff24h

    val verified: Boolean
        get() = token.verified

    val pinned: Boolean
        get() = prefs.pinned

    val hidden: Boolean
        get() = prefs.isHidden

    val index: Int
        get() = prefs.index

    val isTon: Boolean
        get() = (raw as? AssetsEntity.Token)?.token?.isTon ?: false
}
package com.tonapps.legacy.enteties

import android.net.Uri
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class AssetsExtendedEntity(
    val raw: AssetsEntity,
    val prefs: TokenPrefsEntity,
    val accountId: String
) {

    private val token: AccountTokenEntity
        get() = when (raw) {
            is AssetsEntity.Token -> raw.token
            is AssetsEntity.Staked -> AccountTokenEntity(
                balance = BalanceEntity.create(
                    accountId = accountId,
                    value = raw.staked.balance,
                    isRequestMinting = false,
                    isTransferable = true
                ),
            )
            is AssetsEntity.Currency -> AccountTokenEntity(
                balance = BalanceEntity.create(
                    accountId = accountId,
                    value = Coins.ZERO,
                    isRequestMinting = false,
                    isTransferable = false
                ),
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

    val isUsdt: Boolean
        get() = token.isUsdt

    val isTrc20Usdt: Boolean
        get() = token.isTrc20

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

    val blacklist: Boolean
        get() = token.blacklist

    val pinned: Boolean
        get() = prefs.pinned && !prefs.isHidden

    val hidden: Boolean
        get() = prefs.isHidden

    val index: Int
        get() = prefs.index

    private val pinnedOrder: Int
        get() = when {
            !pinned -> Int.MAX_VALUE
            index >= 0 -> index
            isTon -> TON_DEFAULT_PINNED_ORDER
            else -> Int.MAX_VALUE
        }

    private val groupOrder: Int
        get() = when {
            raw is AssetsEntity.Staked -> STAKED_GROUP
            isTrc20Usdt -> TRC20_USDT_GROUP
            isUsdt -> TON_USDT_GROUP
            verified -> VERIFIED_GROUP
            else -> OTHER_GROUP
        }

    val isTon: Boolean
        get() = (raw as? AssetsEntity.Token)?.token?.isTon ?: false

    val isTrx: Boolean
        get() = token.isTrx

    companion object {
        private const val TRC20_USDT_GROUP = 0
        private const val TON_USDT_GROUP = 1
        private const val STAKED_GROUP = 2
        private const val VERIFIED_GROUP = 3
        private const val OTHER_GROUP = 4
        private const val TON_DEFAULT_PINNED_ORDER = -1

        val comparator = compareBy<AssetsExtendedEntity> {
            !it.pinned
        }.thenBy {
            it.pinnedOrder
        }.thenBy {
            it.groupOrder
        }.thenByDescending {
            it.fiat
        }
    }
}
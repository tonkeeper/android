package com.tonapps.wallet.data.token.entities

import android.net.Uri
import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountTokenEntity(
    val balance: BalanceEntity,
    @IgnoredOnParcel
    var rate: TokenRateEntity? = null,
): Parcelable {

    companion object {

        val EMPTY = AccountTokenEntity(
            BalanceEntity(
                TokenEntity.TON,
                Coins.ZERO,
                ""
            )
        )
    }

    val imageUri: Uri
        get() = balance.token.imageUri

    val address: String
        get() = balance.token.address

    val decimals: Int
        get() = balance.token.decimals

    val name: String
        get() = balance.token.name

    val symbol: String
        get() = balance.token.symbol

    val isTon: Boolean
        get() = address == TokenEntity.TON.address

    val isUsdt: Boolean
        get() = address == TokenEntity.USDT.address

    val fiat: Coins
        get() = rate?.fiat ?: Coins.ZERO

    val rateNow: Coins
        get() = rate?.rate ?: Coins.ZERO

    val rateDiff24h: String
        get() = rate?.rateDiff24h ?: ""

    val verified: Boolean
        get() = balance.token.verification == TokenEntity.Verification.whitelist
}
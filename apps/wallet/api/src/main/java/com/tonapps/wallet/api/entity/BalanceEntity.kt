package com.tonapps.wallet.api.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.icu.Coins
import io.tonapi.models.JettonBalance
import io.tonapi.models.TokenRates
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class BalanceEntity(
    val token: TokenEntity,
    val value: Coins,
    val walletAddress: String,
    val initializedAccount: Boolean = true,
    val isRequestMinting: Boolean = false,
    val isTransferable: Boolean = true,
    val lastActivity: Long = -1,
): Parcelable {

    companion object {

        fun empty(
            accountId: String,
            isCompressed: Boolean,
            isTransferable: Boolean
        ) = create(accountId, Coins.ZERO, isCompressed, isTransferable)

        fun create(
            accountId: String,
            value: Coins,
            isRequestMinting: Boolean = false,
            isTransferable: Boolean = true
        ) = BalanceEntity(
            token = TokenEntity.TON,
            value = value,
            walletAddress = accountId,
            initializedAccount = false,
            isRequestMinting = isRequestMinting,
            isTransferable = isTransferable
        )
    }

    @IgnoredOnParcel
    var rates: TokenRates? = null

    val isTon: Boolean
        get() = token.isTon

    val decimals: Int
        get() = token.decimals

    val customPayloadApiUri: String?
        get() = token.customPayloadApiUri

    constructor(jettonBalance: JettonBalance) : this(
        token = TokenEntity(jettonBalance.jetton, jettonBalance.extensions, jettonBalance.lock),
        value = Coins.of(BigDecimal(jettonBalance.balance).movePointLeft(jettonBalance.jetton.decimals), jettonBalance.jetton.decimals),
        walletAddress = jettonBalance.walletAddress.address,
        initializedAccount = true,
        isRequestMinting = jettonBalance.extensions?.contains(TokenEntity.Extension.CustomPayload.value) == true,
        isTransferable = jettonBalance.extensions?.contains(TokenEntity.Extension.NonTransferable.value) != true
    ) {
        rates = jettonBalance.price
    }
}
package com.tonapps.wallet.api.entity

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.value.Blockchain
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
    val numerator: BigDecimal? = null,
    val denominator: BigDecimal? = null,
) : Parcelable {

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

    val blockchain: Blockchain
        get() = token.blockchain

    val uiBalance: Coins
        get() {
            if (numerator == null || denominator == null) {
                return value
            }

            return Coins.of(
                value.value.multiply(numerator).divide(denominator, 18, BigDecimal.ROUND_HALF_UP),
                decimals
            )
        }

    fun fromUIBalance(amount: Coins): Coins {
        if (numerator == null || denominator == null) {
            return amount
        }

        return Coins.of(
            amount.value.multiply(denominator).divide(numerator, 18, BigDecimal.ROUND_HALF_UP),
            decimals
        )
    }

    constructor(jettonBalance: JettonBalance) : this(
        token = TokenEntity(jettonBalance.jetton, jettonBalance.extensions, jettonBalance.lock),
        value = Coins.of(
            BigDecimal(jettonBalance.balance).movePointLeft(jettonBalance.jetton.decimals),
            jettonBalance.jetton.decimals
        ),
        walletAddress = jettonBalance.walletAddress.address,
        initializedAccount = true,
        isRequestMinting = jettonBalance.extensions?.contains(TokenEntity.Extension.CustomPayload.value) == true,
        isTransferable = jettonBalance.extensions?.contains(TokenEntity.Extension.NonTransferable.value) != true,
        numerator = jettonBalance.jetton.scaledUi?.numerator?.toBigDecimal(),
        denominator = jettonBalance.jetton.scaledUi?.denominator?.toBigDecimal()
    ) {
        rates = jettonBalance.price
    }
}
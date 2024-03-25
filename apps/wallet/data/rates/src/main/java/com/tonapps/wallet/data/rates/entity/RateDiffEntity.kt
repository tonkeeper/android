package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.wallet.data.core.WalletCurrency
import io.tonapi.models.TokenRates
import kotlinx.parcelize.Parcelize

@Parcelize
data class RateDiffEntity(
    val diff24h: String,
    val diff7d: String,
    val diff30d: String
): Parcelable {

    constructor(
        currency: WalletCurrency,
        rates: TokenRates
    ) : this(
        diff24h = rates.diff24h?.get(currency.code) ?: "",
        diff7d = rates.diff7d?.get(currency.code) ?: "",
        diff30d = rates.diff30d?.get(currency.code) ?: ""
    )
}
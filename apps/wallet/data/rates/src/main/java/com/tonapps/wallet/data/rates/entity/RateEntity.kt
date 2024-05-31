package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.blockchain.Coins
import com.tonapps.wallet.data.core.WalletCurrency
import io.tonapi.models.TokenRates
import kotlinx.parcelize.Parcelize

@Parcelize
data class RateEntity(
    val tokenCode: String,
    val currency: WalletCurrency,
    val value: Coins,
    val diff: RateDiffEntity
): Parcelable
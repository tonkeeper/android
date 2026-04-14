package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.parcelize.Parcelize

@Parcelize
data class RateEntity(
    val tokenCode: String,
    val currency: WalletCurrency,
    val value: Coins,
    val diff: RateDiffEntity
): Parcelable
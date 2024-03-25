package com.tonapps.wallet.data.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletCurrency(
    val code: String,
    val fiat: Boolean
): Parcelable {

    companion object {

        val FIAT = listOf(
            "USD", "EUR",
            "RUB", "AED",
            "KZT", "UAH",
            "UZS", "GBP",
            "CHF", "CNY",
            "KRW", "IDR",
            "INR", "JPY"
        )

        val CRYPTO = listOf(
            "TON", "BTC",
        )

        val DEFAULT = WalletCurrency(FIAT.first())
        val TON = WalletCurrency("TON")
    }

    constructor(code: String) : this(code, code in FIAT)
}

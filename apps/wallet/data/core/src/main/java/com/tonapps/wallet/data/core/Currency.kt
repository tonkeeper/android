package com.tonapps.wallet.data.core

data class Currency(
    val code: String,
    val fiat: Boolean
) {

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
    }

    constructor(code: String) : this(code, code in FIAT)
}

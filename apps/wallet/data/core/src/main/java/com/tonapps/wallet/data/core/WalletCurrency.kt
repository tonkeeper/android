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
            "USD", // United States Dollar
            "EUR", // Euro
            "RUB", // Russian Ruble
            "AED", // United Arab Emirates Dirham
            "UAH", // Ukrainian Hryvnia
            "KZT", // Kazakhstani Tenge
            "UZS", // Uzbekistani sum
            "GBP", // Great Britain Pound
            "CHF", // Swiss Franc
            "CNY", // China Yuan
            "GEL", // Georgian Lari
            "KRW", // South Korean Won
            "IDR", // Indonesian Rupiah
            "INR", // Indian Rupee
            "JPY", // Japanese Yen
            "CAD", // Canadian Dollar
            "ARS", // Argentine Peso
            "BYN", // Belarusian Ruble
            "COP", // Colombian Peso
            "ETB", // Ethiopian Birr
            "ILS", // Israeli Shekel
            "KES", // Kenyan Shilling
            "NGN", // Nigerian Naira
            "UGX", // Ugandan Shilling
            "VES", // Venezuelan Bolivar
            "ZAR", // South African Rand
            "TRY", // Turkish Lira
            "THB", // Thai Baht
            "VND", // Vietnamese Dong
            "BRL", // Brazilian Real
            "BDT", // Bangladeshi Taka
        )

        val CRYPTO = listOf(
            "TON", "BTC",
        )

        val DEFAULT = WalletCurrency(FIAT.first())
        val TON = WalletCurrency("TON")

        val USD = WalletCurrency("USD", true)

        val ALL = FIAT + CRYPTO

        fun of(code: String?): WalletCurrency {
            if (code.isNullOrBlank()) {
                return DEFAULT
            }
            if (code in CRYPTO) {
                return WalletCurrency(code)
            }
            if (code in FIAT) {
                return WalletCurrency(code)
            }
            return DEFAULT
        }
    }

    constructor(code: String) : this(code, code in FIAT)

    val decimals: Int
        get() = when (code) {
            "TON" -> 9
            "BTC" -> 8
            else -> 6
        }
}

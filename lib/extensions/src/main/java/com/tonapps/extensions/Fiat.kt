package com.tonapps.extensions

private val fiatSymbols = mapOf(
    "USD" to "$",
    "EUR" to "€",
    "RUB" to "₽",
    "AED" to "د.إ",
    "UAH" to "₴",
    "KZT" to "₸",
    "UZS" to "UZS",
    "GBP" to "£",
    "CHF" to "₣",
    "CNY" to "¥",
    "KRW" to "₩",
    "IDR" to "Rp",
    "INR" to "₹",
    "JPY" to "¥",
    "CAD" to "C$",
    "ARS" to "ARS$",
    "BYN" to "Br",
    "COP" to "COL$",
    "ETB" to "ብር",
    "ILS" to "₪",
    "KES" to "KSh",
    "NGN" to "₦",
    "UGX" to "USh",
    "VES" to "Bs.\u200E",
    "ZAR" to "R",
    "TRY" to "₺",
    "THB" to "฿",
    "VND" to "₫",
    "BRL" to "R$",
    "GEL" to "₾",
    "BDT" to "৳"
)

fun String.fiatSymbol(): String {
    return fiatSymbols[this.uppercase()] ?: this
}
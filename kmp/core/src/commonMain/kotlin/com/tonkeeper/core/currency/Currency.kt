package com.tonkeeper.core.currency

sealed interface Currency {
    val name: String
    val code: String
    val symbol: String?
    val decimals: Int
}
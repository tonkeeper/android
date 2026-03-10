package com.tonkeeper.core.currency

data class Fiat(
    override val name: String,
    override val code: String,
    override val symbol: String? = null,
    override val decimals: Int = 2
): Currency {

    companion object {
        val USD = Fiat("US Dollar", "USD", "$")
        val EUR = Fiat("Euro", "EUR", "€")
    }
}
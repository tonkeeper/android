package com.tonapps.wallet.api.entity

import io.tonendpointapi.models.Currency

data class CurrencyEntity(
    val code: String,
    val name: String,
    val image: String?,
    val type: String,
    val symbol: String?,
) {

    val isFiat: Boolean
        get() = type.equals(TYPE_FIAT, ignoreCase = true)

    companion object {
        const val TYPE_FIAT = "fiat"
        const val TYPE_CRYPTO = "crypto"

        fun of(currency: Currency): CurrencyEntity? {
            val code = currency.code.orEmpty().trim().uppercase()
            if (code.isEmpty()) {
                return null
            }
            return CurrencyEntity(
                code = code,
                name = currency.name.orEmpty().trim().ifEmpty { code },
                image = currency.image?.takeIf { it.isNotBlank() },
                type = currency.type.orEmpty().ifBlank { TYPE_FIAT },
                symbol = currency.symbol?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        fun parse(currencies: List<Currency>): List<CurrencyEntity> {
            return currencies.mapNotNull(::of).distinctBy { it.code }
        }
    }
}

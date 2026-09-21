package com.tonapps.core.extensions

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.extensions.fiatSymbol
import com.tonapps.icu.Coins

fun Coins.toDisplayUnit(): DisplayUnit {
    return DisplayUnit(BigDecimal.parseString(value.toPlainString()), Decimal(decimals))
}

fun Coins.formatFiat(currencyCode: String): String {
    return Formatter.formatFiat(
        value = toDisplayUnit(),
        rate = FiatRate(BigDecimal.ONE, FiatCurrency(currencyCode.fiatSymbol())),
    )
}

fun Coins.formatFiat(rate: FiatRate): String {
    return Formatter.formatFiat(value = toDisplayUnit(), rate = rate)
}

fun Coins.formatWithSymbol(symbol: String): String {
    return "${Formatter.formatShort(value = toDisplayUnit())} $symbol"
}

fun Coins.formatFullWithSymbol(symbol: String): String {
    return "${Formatter.formatAsset(value = toDisplayUnit())} $symbol"
}

fun fiatRate(currencyCode: String, price: Coins): FiatRate {
    return FiatRate(
        value = BigDecimal.parseString(price.value.toPlainString()),
        currency = FiatCurrency(currencyCode.fiatSymbol()),
    )
}

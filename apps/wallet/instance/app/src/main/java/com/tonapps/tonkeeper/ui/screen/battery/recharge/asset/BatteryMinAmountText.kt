package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.chainkit.core.chain.model.num.FormatOptions
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.ScaleSpec
import java.math.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal as ChainKitBigDecimal

private const val MIN_AMOUNT_SIGNIFICANT_DIGITS = 3

/**
 * The method's least purchase as the screen names it: [min] whole units shortened to a few significant
 * decimals, never past the asset's [decimals], and rounded up, never down — the amount shown is one the
 * check against the exact minimum takes (`0.030314279` reads "0.0304 SOL", not "0.03 SOL").
 * [symbol] is appended as given, and [options] decide the separators: pass what the amounts beside it carry.
 */
internal fun formatMinAmount(
    symbol: String,
    min: BigDecimal,
    decimals: Int,
    options: FormatOptions = Formatter.defaultOptions,
): String {
    val digits = Formatter.formatNumber(
        value = ChainKitBigDecimal.parseString(min.toPlainString()),
        spec = ScaleSpec.Significant(
            digits = MIN_AMOUNT_SIGNIFICANT_DIGITS,
            maxScale = decimals.coerceAtLeast(0),
            rounding = RoundingMode.CEILING,
        ),
        options = options,
    )
    return if (symbol.isEmpty()) {
        digits
    } else {
        "$digits $symbol"
    }
}

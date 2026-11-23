package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.ScaleSpec
import com.tonapps.wallet.localization.Localization
import java.math.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal as ChainKitBigDecimal

@Composable
fun insufficientNetworkFeeText(symbol: String, required: BigDecimal?): String {
    if (required == null) {
        return stringResource(Localization.perps_deposit_insufficient_network_fee, symbol)
    }
    return stringResource(Localization.perps_deposit_insufficient_network_fee_required, symbol, formatRequiredAmount(required))
}

internal fun formatRequiredAmount(required: BigDecimal): String {
    return Formatter.formatNumber(
        value = ChainKitBigDecimal.parseString(required.toPlainString()),
        spec = ScaleSpec.Significant(
            digits = REQUIRED_SIGNIFICANT_DIGITS,
            maxScale = required.scale().coerceAtLeast(0),
            rounding = RoundingMode.CEILING,
        ),
        options = Formatter.defaultOptions,
    )
}

private const val REQUIRED_SIGNIFICANT_DIGITS = 6

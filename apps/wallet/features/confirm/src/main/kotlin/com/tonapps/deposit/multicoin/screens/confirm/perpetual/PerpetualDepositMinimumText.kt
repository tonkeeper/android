package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.wallet.localization.Localization
import java.math.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal as ChainKitBigDecimal

@Composable
internal fun perpetualDepositMinimumText(minUsd: BigDecimal?): String {
    if (minUsd == null) {
        return stringResource(Localization.perps_deposit_min_amount)
    }
    val formatted = Formatter.formatFiat(
        value = ChainKitBigDecimal.parseString(minUsd.toPlainString()),
        currency = USD,
        options = usdFormatOptions(),
    )
    return stringResource(Localization.perps_deposit_min_amount_usd, formatted)
}

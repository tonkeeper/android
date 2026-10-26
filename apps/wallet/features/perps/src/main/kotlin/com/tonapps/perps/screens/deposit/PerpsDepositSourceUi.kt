package com.tonapps.perps.screens.deposit

import androidx.compose.runtime.Immutable
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.formatFiatAmount
import com.tonapps.perps.data.formatTokenCompact
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.funding.PerpsDepositSource

@Immutable
data class PerpsDepositSourceUi(
    val source: PerpsDepositSource,
    val balanceFormat: String,
    val fiatFormat: String,
)

internal fun PerpsDepositSource.toUi(hiddenBalance: Boolean): PerpsDepositSourceUi = PerpsDepositSourceUi(
    source = this,
    balanceFormat = maskIf(hiddenBalance) { formatTokenCompact(balance, symbol) },
    fiatFormat = maskIf(hiddenBalance) { formatFiatAmount(fiatValue, account.rate?.currencyCode ?: USD_CURRENCY) },
)

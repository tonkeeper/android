package com.tonapps.deposit.multicoin.screens.ramp.amount

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.screens.ramp.RampType

data class RampAmountData(
    val rampType: RampType,
    val assetId: String,
    val paymentMethodType: String,
    val analyticsFrom: DepositFlowFrom,
    /** Preferred fiat (e.g. from the chosen layout card). Falls back to the wallet currency. */
    val fiat: String? = null,
)

package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualAccountSnapshot
import com.tonapps.chainkit.perpetual.PerpetualOperation
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.perps.data.PerpsError
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import io.perpsapi.models.Integrator
import java.math.BigDecimal

data class PerpsMarketState(
    val rules: PerpsMarketRules,
    val context: PerpetualAccountSnapshot,
)

data class PerpsPlannedTrade(
    val request: PerpsTradeRequest,
    val credentials: LighterSigningCredentials,
    val intent: PerpsTradeIntent,
    val rules: PerpsMarketRules,
    val context: PerpetualAccountSnapshot,
    val plan: PerpetualOperation,
    val integrator: Integrator,
    val snapshotAtMs: Long,
    val markPrice: BigDecimal,
)

sealed interface PerpsPlanFailure {
    data class Planner(
        val kind: PerpsTradeError,
        val message: String,
        val field: String?,
        val suggestion: Long?,
        val priceDecimals: Int?,
    ) : PerpsPlanFailure

    data class Load(val error: PerpsError) : PerpsPlanFailure

    data object ClockSkew : PerpsPlanFailure
}

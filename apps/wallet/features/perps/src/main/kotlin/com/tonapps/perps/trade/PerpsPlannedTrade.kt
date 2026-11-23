package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualAccountSnapshot
import com.tonapps.chainkit.perpetual.PerpetualOperation
import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsAppliedPrerequisite
import com.tonapps.chainkit.perpetual.PerpsFeeKind
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsMarkQuote
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import io.perpsapi.models.Integrator
import java.math.BigDecimal

interface PerpsTradePlanner {

    suspend fun plan(
        request: PerpsTradeRequest,
        credentials: LighterSigningCredentials,
        mark: PerpsMarkQuote?,
        operationId: String,
        applied: List<PerpsAppliedPrerequisite> = emptyList(),
        previous: PerpsTradeIntent? = null,
    ): KResult<PerpsPlannedTrade, PerpsPlanFailure>

    suspend fun autoCloseRenewalDue(
        walletId: String,
        marketIndex: Int,
        credentials: LighterSigningCredentials,
        nowMs: Long,
        confirmed: Map<Long, Long>,
    ): KResult<Boolean, PerpsError>
}

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

internal fun PerpsPlannedTrade.displayedTakerFeePpm(kind: PerpsFeeKind): Long =
    displayedTakerFeePpm(kind, rules, integrator)

internal fun displayedTakerFeePpm(kind: PerpsFeeKind, rules: PerpsMarketRules, integrator: Integrator): Long =
    (integrator.venueTakerFee?.toLong() ?: rules.takerFeePpm) + rules.integratorRates(kind).takerFeePpm

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

internal val PERPS_RETRYABLE_PLAN_KINDS = setOf(
    PerpsTradeError.StaleInput,
    PerpsTradeError.PlanExpired,
    PerpsTradeError.PositionChanged,
    PerpsTradeError.UnusableSnapshot,
    PerpsTradeError.NeedsMoreDepth,
)

internal fun PerpsPlannedTrade.closesWhole(): Boolean = (plan.review as? PerpetualReview.Close)?.fullExit == true

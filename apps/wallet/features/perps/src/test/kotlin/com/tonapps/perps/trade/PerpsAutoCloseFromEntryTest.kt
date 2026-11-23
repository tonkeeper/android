package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.perpetual.PerpetualAccountSnapshot
import com.tonapps.chainkit.perpetual.PerpsAppliedPrerequisite
import com.tonapps.chainkit.perpetual.PerpsMarginSettings
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsMarkQuote
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseFromEntryTest : BaseUnitTest() {

    private val planner = FakePlanner()
    private val credentials = mockk<LighterSigningCredentials>()
    private val planned = mockk<PerpsPlannedTrade>()
    private val starts = mutableListOf<PerpsPlannedTrade>()
    private var startResult: (attempt: Int) -> PerpsSubmitStart = { PerpsSubmitStart.Started("op") }

    @Test
    fun `a market open with percent legs places them again from the entry`() = runTest {
        entry().afterFill(opened(open()))

        val request = planner.requests.single() as PerpsTradeRequest.AutoCloseFromEntry
        assertEquals(WALLET_ID, request.walletId)
        assertEquals(MARKET_INDEX, request.marketIndex)
        assertEquals(LEVERAGE, request.leverage)
        assertEquals(PERCENT_DRAFT, request.placed)
        assertEquals(listOf(planned), starts)
    }

    @Test
    fun `a limit open with percent legs places them again from the entry`() = runTest {
        entry().afterFill(opened(open(limitPrice = BigDecimal("80000"))))

        val request = planner.requests.single() as PerpsTradeRequest.AutoCloseFromEntry
        assertEquals(PERCENT_DRAFT, request.placed)
        assertEquals(1, starts.size)
    }

    @Test
    fun `an add with percent legs places them again at the position leverage`() = runTest {
        entry().afterFill(opened(add(PerpsAutoCloseEdit.Set(PERCENT_DRAFT)), marginSettings = known(POSITION_LEVERAGE)))

        val request = planner.requests.single() as PerpsTradeRequest.AutoCloseFromEntry
        assertEquals(POSITION_LEVERAGE, request.leverage)
        assertEquals(PERCENT_DRAFT, request.placed)
        assertEquals(1, starts.size)
    }

    @Test
    fun `trades without percent legs are left as placed`() = runTest {
        val entry = entry()

        entry.afterFill(opened(open(autoClose = null)))
        entry.afterFill(opened(open(autoClose = PERCENT_DRAFT.copy(takeProfitRoi = null, stopLossRoi = null))))
        entry.afterFill(opened(add(PerpsAutoCloseEdit.Keep), marginSettings = known(POSITION_LEVERAGE)))
        entry.afterFill(opened(add(PerpsAutoCloseEdit.Set(PERCENT_DRAFT)), marginSettings = PerpsMarginSettings.Unknown))
        entry.afterFill(opened(PerpsTradeRequest.AutoCloseRenewal(WALLET_ID, MARKET_INDEX, emptyMap())))

        assertTrue(planner.requests.isEmpty())
        assertTrue(starts.isEmpty())
    }

    @Test
    fun `legs the venue does not show yet are planned again under the same operation`() = runTest {
        planner.plans = { attempt ->
            if (attempt < 3) {
                KResult.Err(plannerFailure(PerpsTradeError.OrderNotFound))
            } else {
                KResult.Ok(planned)
            }
        }

        entry().afterFill(opened(open()))

        assertEquals(3, planner.operationIds.size)
        assertEquals(1, planner.operationIds.distinct().size)
        assertTrue(isRenewalOperation(planner.operationIds.first()))
        assertEquals(1, starts.size)
    }

    @Test
    fun `a blocked start is tried again`() = runTest {
        startResult = { attempt ->
            if (attempt == 1) {
                PerpsSubmitStart.Blocked("other")
            } else {
                PerpsSubmitStart.Started("op")
            }
        }

        entry().afterFill(opened(open()))

        assertEquals(2, starts.size)
    }

    @Test
    fun `a final planner refusal stops at once`() = runTest {
        planner.plans = { KResult.Err(plannerFailure(PerpsTradeError.InvalidAutoClose)) }

        entry().afterFill(opened(open()))

        assertEquals(1, planner.operationIds.size)
        assertTrue(starts.isEmpty())
    }

    @Test
    fun `a refused send is not repeated`() = runTest {
        startResult = { PerpsSubmitStart.Failed(PerpsTradeFailure.SendRefused(PerpsSendReason.LIGHTER_REJECTED, "code")) }

        entry().afterFill(opened(open()))

        assertEquals(1, starts.size)
    }

    @Test
    fun `it gives up after a bounded number of attempts`() = runTest {
        planner.plans = { KResult.Err(PerpsPlanFailure.Load(PerpsError.Unknown)) }

        entry().afterFill(opened(open()))

        assertEquals(ATTEMPTS, planner.operationIds.size)
        assertTrue(starts.isEmpty())
    }

    private fun entry() = PerpsAutoCloseFromEntry(
        planner = planner,
        start = { trade ->
            starts += trade
            startResult(starts.size)
        },
        pacer = { _, _, attempt ->
            while (!attempt()) {
                delay(RETRY_DELAY_MS)
            }
        },
    )

    private fun opened(
        request: PerpsTradeRequest,
        marginSettings: PerpsMarginSettings = PerpsMarginSettings.Unknown,
    ): PerpsPlannedTrade {
        val context = mockk<PerpetualAccountSnapshot>()
        every { context.marginSettings } returns marginSettings
        val trade = mockk<PerpsPlannedTrade>()
        every { trade.request } returns request
        every { trade.credentials } returns credentials
        every { trade.context } returns context
        return trade
    }

    private fun add(autoClose: PerpsAutoCloseEdit) = PerpsTradeRequest.Add(
        walletId = WALLET_ID,
        marketIndex = MARKET_INDEX,
        marginDelta = BigDecimal.ONE,
        autoClose = autoClose,
    )

    private fun known(leverage: Int) = PerpsMarginSettings.Known(
        mode = LighterConstants.IsolatedMargin,
        initialMarginBps = PerpsProductRules.initialMarginBps(leverage),
    )

    private fun open(
        autoClose: PerpsAutoCloseDraft? = PERCENT_DRAFT,
        limitPrice: BigDecimal? = null,
    ) = PerpsTradeRequest.Open(
        walletId = WALLET_ID,
        marketIndex = MARKET_INDEX,
        side = PerpsOrderSide.LONG,
        marginQuote = BigDecimal.ONE,
        leverage = LEVERAGE,
        autoClose = autoClose,
        limitPrice = limitPrice,
    )

    private fun plannerFailure(kind: PerpsTradeError) = PerpsPlanFailure.Planner(
        kind = kind,
        message = kind.name,
        field = null,
        suggestion = null,
        priceDecimals = null,
    )

    private inner class FakePlanner : PerpsTradePlanner {

        var plans: (attempt: Int) -> KResult<PerpsPlannedTrade, PerpsPlanFailure> = { KResult.Ok(planned) }
        val requests = mutableListOf<PerpsTradeRequest>()
        val operationIds = mutableListOf<String>()

        override suspend fun plan(
            request: PerpsTradeRequest,
            credentials: LighterSigningCredentials,
            mark: PerpsMarkQuote?,
            operationId: String,
            applied: List<PerpsAppliedPrerequisite>,
            previous: PerpsTradeIntent?,
        ): KResult<PerpsPlannedTrade, PerpsPlanFailure> {
            requests += request
            operationIds += operationId
            return plans(operationIds.size)
        }

        override suspend fun autoCloseRenewalDue(
            walletId: String,
            marketIndex: Int,
            credentials: LighterSigningCredentials,
            nowMs: Long,
            confirmed: Map<Long, Long>,
        ): KResult<Boolean, PerpsError> = KResult.Ok(false)
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 1
        const val LEVERAGE = 50
        const val POSITION_LEVERAGE = 20
        const val ATTEMPTS = 6
        const val RETRY_DELAY_MS = 2_000L
        val PERCENT_DRAFT = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("82847.3"),
            stopLoss = BigDecimal("81862.9"),
            takeProfitRoi = BigDecimal("50"),
            stopLossRoi = BigDecimal("-10"),
        )
    }
}

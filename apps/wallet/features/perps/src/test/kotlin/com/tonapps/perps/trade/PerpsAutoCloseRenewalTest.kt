package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsAppliedPrerequisite
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsMarkQuote
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsAutoCloseRenewalTest : BaseUnitTest() {

    private val planner = FakePlanner()
    private val executor = mockk<PerpsTradeExecutor>()
    private val perpetualRepository = mockk<PerpetualRepository>()
    private val credentials = mockk<LighterSigningCredentials>()
    private val planned = mockk<PerpsPlannedTrade>()
    private val confirmedAutoClose = mockk<PerpsConfirmedAutoCloseStore> {
        every { confirmed(any()) } returns emptyMap()
    }
    private var now = START_MS

    @Test
    fun `a wallet is checked at most once an hour`() = runTest {
        val renewal = renewal()
        givenCredentials()

        renewNow(renewal)
        now += HOUR_MS - 1
        renewNow(renewal)
        renewNow(renewal, OTHER_WALLET_ID)

        assertEquals(listOf(WALLET_ID, OTHER_WALLET_ID), planner.dueChecks)

        now += 1
        renewNow(renewal)

        assertEquals(listOf(WALLET_ID, OTHER_WALLET_ID, WALLET_ID), planner.dueChecks)
    }

    @Test
    fun `a load error retries after a minute`() = runTest {
        val renewal = renewal()
        givenCredentials()
        planner.due = KResult.Err(PerpsError.Unknown)

        renewNow(renewal)
        now += RETRY_MS - 1
        renewNow(renewal)

        assertEquals(1, planner.dueChecks.size)

        now += 1
        renewNow(renewal)

        assertEquals(2, planner.dueChecks.size)
    }

    @Test
    fun `requotes that run out retry after a minute`() = runTest {
        val renewal = renewal()
        givenCredentials()
        planner.due = KResult.Ok(true)
        givenStarts(PerpsSubmitStart.Requote(PerpsTradeError.StaleInput))

        renewNow(renewal)

        coVerify(exactly = 2) { executor.start(planned, any()) }

        now += RETRY_MS
        renewNow(renewal)

        assertEquals(2, planner.dueChecks.size)
    }

    @Test
    fun `a retryable planner refusal plans again under the same operation`() = runTest {
        val renewal = renewal()
        givenCredentials()
        planner.due = KResult.Ok(true)
        planner.plans = { attempt ->
            if (attempt == 1) {
                KResult.Err(plannerFailure(PerpsTradeError.StaleInput))
            } else {
                KResult.Ok(planned)
            }
        }
        givenStarts(PerpsSubmitStart.Started("op"))

        renewNow(renewal)

        assertEquals(2, planner.operationIds.size)
        assertEquals(1, planner.operationIds.distinct().size)
        assertTrue(isRenewalOperation(planner.operationIds.first()))
        coVerify(exactly = 1) { executor.start(planned, any()) }

        now += HOUR_MS - 1
        renewNow(renewal)

        assertEquals(1, planner.dueChecks.size)
    }

    @Test
    fun `a refusal waits the full hour`() = runTest {
        val renewal = renewal()
        givenCredentials()
        planner.due = KResult.Ok(true)
        givenStarts(PerpsSubmitStart.Failed(PerpsTradeFailure.SendRefused(PerpsSendReason.LIGHTER_REJECTED, "code")))

        renewNow(renewal)
        now += HOUR_MS - 1
        renewNow(renewal)

        assertEquals(1, planner.dueChecks.size)
    }

    @Test
    fun `a final planner refusal waits the full hour`() = runTest {
        val renewal = renewal()
        givenCredentials()
        planner.due = KResult.Ok(true)
        planner.plans = { KResult.Err(plannerFailure(PerpsTradeError.InvalidAutoClose)) }

        renewNow(renewal)
        now += HOUR_MS - 1
        renewNow(renewal)

        assertEquals(1, planner.operationIds.size)
        assertEquals(1, planner.dueChecks.size)
    }

    @Test
    fun `no credentials waits the full hour`() = runTest {
        val renewal = renewal()
        coEvery { perpetualRepository.signingCredentials(any()) } returns null

        renewNow(renewal)
        now += HOUR_MS - 1
        renewNow(renewal)

        coVerify(exactly = 1) { perpetualRepository.signingCredentials(WALLET_ID) }
        assertTrue(planner.dueChecks.isEmpty())
    }

    @Test
    fun `the renewal never asks to unlock`() = runTest {
        val renewal = renewal()
        var unlocked: PerpsOwnerMessageSigner? = PerpsOwnerMessageSigner { "0x" }
        givenCredentials()
        planner.due = KResult.Ok(true)
        coEvery { executor.start(planned, any()) } coAnswers {
            unlocked = secondArg<suspend () -> PerpsOwnerMessageSigner?>().invoke()
            PerpsSubmitStart.Failed(PerpsTradeFailure.SendRefused(PerpsSendReason.INTEGRATOR_NOT_APPROVED, "code"))
        }

        renewNow(renewal)

        assertNull(unlocked)
    }

    private fun TestScope.renewal() =
        PerpsAutoCloseRenewal(planner, executor, perpetualRepository, confirmedAutoClose, this) { now }

    private fun TestScope.renewNow(renewal: PerpsAutoCloseRenewal, walletId: String = WALLET_ID) {
        renewal.renewInBackground(walletId, listOf(MARKET_INDEX))
        runCurrent()
    }

    private fun givenCredentials() {
        coEvery { perpetualRepository.signingCredentials(any()) } returns credentials
    }

    private fun givenStarts(start: PerpsSubmitStart) {
        coEvery { executor.start(planned, any()) } returns start
    }

    private fun plannerFailure(kind: PerpsTradeError) = PerpsPlanFailure.Planner(
        kind = kind,
        message = kind.name,
        field = null,
        suggestion = null,
        priceDecimals = null,
    )

    // mockk cannot return KResult from a suspend stub: it fails unboxing the generic value class.
    private inner class FakePlanner : PerpsTradePlanner {

        var due: KResult<Boolean, PerpsError> = KResult.Ok(false)
        var plans: (attempt: Int) -> KResult<PerpsPlannedTrade, PerpsPlanFailure> = { KResult.Ok(planned) }
        val dueChecks = mutableListOf<String>()
        val operationIds = mutableListOf<String>()

        override suspend fun plan(
            request: PerpsTradeRequest,
            credentials: LighterSigningCredentials,
            mark: PerpsMarkQuote?,
            operationId: String,
            applied: List<PerpsAppliedPrerequisite>,
            previous: PerpsTradeIntent?,
        ): KResult<PerpsPlannedTrade, PerpsPlanFailure> {
            operationIds += operationId
            return plans(operationIds.size)
        }

        override suspend fun autoCloseRenewalDue(
            walletId: String,
            marketIndex: Int,
            credentials: LighterSigningCredentials,
            nowMs: Long,
            confirmed: Map<Long, Long>,
        ): KResult<Boolean, PerpsError> {
            dueChecks += walletId
            return due
        }
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val OTHER_WALLET_ID = "other-wallet"
        const val MARKET_INDEX = 1
        const val START_MS = 1_800_000_000_000L
        const val HOUR_MS = 60 * 60 * 1000L
        const val RETRY_MS = 60_000L
    }
}

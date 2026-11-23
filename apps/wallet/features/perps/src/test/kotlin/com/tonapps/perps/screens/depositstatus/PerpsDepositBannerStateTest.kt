package com.tonapps.perps.screens.depositstatus

import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.funding.PerpsDepositStatus
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsDepositBannerStateTest {

    @Test
    fun `one deposit shows its step until it arrives`() {
        val state = state(deposit("a"))
        assertEquals(PerpsDepositBanner.Single("a"), state.banner)

        val arrived = state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))

        assertEquals(PerpsDepositBanner.Completed(setOf("a")), state.banner)
        assertEquals(setOf("a"), arrived)
        assertEquals(
            PerpsDepositRowUi.Completed(setOf("a"), formatSignedUsdAmount(BigDecimal("10"))),
            state.rowUi(now = 0, hiddenBalance = false),
        )

        val again = state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))
        assertEquals(PerpsDepositBanner.Completed(setOf("a")), state.banner)
        assertEquals(emptySet<String>(), again)
    }

    @Test
    fun `a deposit credited before the screen saw it is not announced`() {
        val state = state(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"))

        val arrived = state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))

        assertNull(state.banner)
        assertEquals(emptySet<String>(), arrived)
    }

    @Test
    fun `multiple becomes single when only one deposit is pending`() {
        val state = state(deposit("a"), deposit("b"), deposit("c"))
        assertEquals(PerpsDepositBanner.Multiple, state.banner)

        val first = state.apply(
            listOf(deposit("b"), deposit("c"), deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "4")),
        )
        assertEquals(PerpsDepositBanner.Multiple, state.banner)
        assertEquals(setOf("a"), first)

        state.apply(
            listOf(
                deposit("c"),
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "4"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "6"),
            ),
        )
        assertEquals(PerpsDepositBanner.Single("c"), state.banner)

        state.apply(
            listOf(
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "4"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "6"),
                deposit("c", PerpsDepositStatus.State.COMPLETED, credited = "1"),
            ),
        )
        assertEquals(PerpsDepositBanner.Completed(setOf("c")), state.banner)
        assertEquals(
            PerpsDepositRowUi.Completed(setOf("c"), formatSignedUsdAmount(BigDecimal("1"))),
            state.rowUi(now = 0, hiddenBalance = false),
        )
    }

    @Test
    fun `completion waits for the balance refresh`() {
        val state = state(deposit("a"))
        val credited = deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")

        val whileRefreshing = state.apply(listOf(credited), isBalanceRefreshing = true)
        assertEquals(PerpsDepositBanner.Single("a"), state.banner)
        assertEquals(PerpsDepositStatus.State.PENDING, state.deposit("a")?.state)
        assertEquals(emptySet<String>(), whileRefreshing)

        state.didInteract()
        assertEquals(PerpsDepositBanner.Single("a"), state.banner)

        val refreshed = state.apply(listOf(credited))
        assertEquals(PerpsDepositBanner.Completed(setOf("a")), state.banner)
        assertEquals(setOf("a"), refreshed)
    }

    @Test
    fun `an unknown credited amount does not use the expected amount`() {
        val state = state(deposit("a"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, expected = "10")))

        assertEquals(PerpsDepositRowUi.Completed(setOf("a"), amountFormat = null), state.rowUi(0, hiddenBalance = false))
    }

    @Test
    fun `deposits that finish together sum`() {
        val state = state(deposit("a"), deposit("b"))
        state.apply(
            listOf(
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "2.5"),
            ),
        )

        assertEquals(PerpsDepositBanner.Completed(setOf("a", "b")), state.banner)
        assertEquals(
            PerpsDepositRowUi.Completed(setOf("a", "b"), formatSignedUsdAmount(BigDecimal("12.5"))),
            state.rowUi(now = 0, hiddenBalance = false),
        )
    }

    @Test
    fun `a failure stays until the sheet opens and a look at the pending one does not count`() {
        val state = state(deposit("a"))
        state.didOpen("a")
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.REFUNDED)))

        assertEquals(PerpsDepositBanner.Single("a"), state.banner)
        assertEquals(PerpsDepositRowUi.Problem("a", unconfirmed = false), state.rowUi(0, hiddenBalance = false))

        state.didOpen("a")
        assertNull(state.banner)
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.REFUNDED)))
        assertNull(state.banner)
    }

    @Test
    fun `a failure the journal marks as seen is not shown again`() {
        val next = PerpsDepositBannerState(isVisible = true)
        next.apply(listOf(deposit("a", PerpsDepositStatus.State.UNCONFIRMED, acknowledged = true)))

        assertNull(next.banner)
    }

    @Test
    fun `opening a problem returns it to persist and a pending deposit returns nothing`() {
        val state = state(deposit("a"), deposit("b", PerpsDepositStatus.State.FAILED))

        assertEquals(emptySet<String>(), state.didOpen("a"))
        assertEquals(setOf("b"), state.didOpen("b"))
    }

    @Test
    fun `failures from a group show as statuses until the list opens`() {
        val state = state(deposit("a"), deposit("b"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.FAILED), deposit("b", PerpsDepositStatus.State.REFUNDED)))
        assertEquals(PerpsDepositBanner.Problems, state.banner)
        assertEquals(PerpsDepositRowUi.Problems, state.rowUi(0, hiddenBalance = false))

        assertEquals(setOf("a", "b"), state.didOpenList())
        assertNull(state.banner)
    }

    @Test
    fun `clearing the completed row shows a failure that was waiting`() {
        val state = state(deposit("a"), deposit("b"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.FAILED), deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "3")))
        assertEquals(PerpsDepositBanner.Completed(setOf("b")), state.banner)

        state.didInteract()

        assertEquals(PerpsDepositBanner.Problems, state.banner)
    }

    @Test
    fun `multiple deposit failures stay in the list`() {
        val state = state(deposit("a"), deposit("b"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.FAILED), deposit("b")))
        assertEquals(PerpsDepositBanner.Single("b"), state.banner)

        state.apply(listOf(deposit("a", PerpsDepositStatus.State.FAILED), deposit("b", PerpsDepositStatus.State.FAILED)))
        assertEquals(PerpsDepositBanner.Problems, state.banner)
        assertEquals(2, state.deposits.count { it.state == PerpsDepositStatus.State.FAILED })
    }

    @Test
    fun `the list hides deposits seen when it last closed`() {
        val state = state(
            deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"),
            deposit("b"),
            deposit("c", PerpsDepositStatus.State.FAILED),
        )
        state.didOpenList()
        assertEquals(setOf("a", "b", "c"), state.listedIds())
        state.apply(
            listOf(
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "5"),
                deposit("c", PerpsDepositStatus.State.FAILED),
                deposit("d"),
            ),
        )
        assertEquals(setOf("a", "b"), state.didCloseList())

        state.didOpenList()
        assertEquals(setOf("d"), state.listedIds())
        state.apply(
            listOf(
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "5"),
                deposit("c", PerpsDepositStatus.State.FAILED),
                deposit("d", PerpsDepositStatus.State.COMPLETED, credited = "1"),
            ),
        )
        assertEquals(setOf("d"), state.listedIds())
        assertEquals(setOf("d"), state.didCloseList())

        state.didOpenList()
        assertEquals(emptySet<String>(), state.listedIds())
    }

    @Test
    fun `the list opened from the completed row shows the deposits it announces`() {
        val state = state(deposit("a"), deposit("b"))
        state.didOpenList()
        state.apply(
            listOf(
                deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10"),
                deposit("b", PerpsDepositStatus.State.COMPLETED, credited = "5"),
            ),
        )
        state.didCloseList()
        assertEquals(PerpsDepositBanner.Completed(setOf("a", "b")), state.banner)

        state.didOpenList(announced = setOf("a", "b"))
        assertEquals(setOf("a", "b"), state.listedIds())
        state.didInteract()
        assertEquals(emptySet<String>(), state.didCloseList())

        state.didOpenList()
        assertEquals(emptySet<String>(), state.listedIds())
    }

    @Test
    fun `deposits the journal marks as seen are hidden but pending ones stay listed`() {
        val state = state(
            deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10", acknowledged = true),
            deposit("b", PerpsDepositStatus.State.REFUNDED, acknowledged = true),
            deposit("c", acknowledged = true),
            deposit("d"),
        )

        state.didOpenList()

        assertEquals(setOf("c", "d"), state.listedIds())
        assertEquals(emptySet<String>(), state.didCloseList())
    }

    @Test
    fun `away from perps the arrival is only announced`() {
        val state = state(deposit("a"))
        state.isVisible = false

        val arrived = state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))

        assertNull(state.banner)
        assertEquals(setOf("a"), arrived)
    }

    @Test
    fun `the completed row leaves on the next action`() {
        val state = state(deposit("a"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))

        state.didInteract()

        assertNull(state.banner)
    }

    @Test
    fun `no estimate never counts as delayed`() {
        val now = 10_000_000L
        val missing = deposit("a", createdAt = now - 10_000_000, estimated = null)
        val waiting = deposit("a", createdAt = now - 30_000, estimated = 120)
        val late = deposit("a", createdAt = now - 121_000, estimated = 120)
        val completed = deposit("a", PerpsDepositStatus.State.COMPLETED, createdAt = now - 121_000, estimated = 120)

        assertFalse(missing.isDelayed(now))
        assertFalse(waiting.isDelayed(now))
        assertTrue(late.isDelayed(now))
        assertFalse(completed.isDelayed(now))
    }

    @Test
    fun `a pending row shows its step estimate and turns delayed past it`() {
        val state = state(deposit("a", createdAt = 0, estimated = 146, step = PerpsDepositPendingStep.CONVERTING))

        assertEquals(
            PerpsDepositRowUi.Step("a", PerpsDepositPendingStep.CONVERTING, "10 TON", etaMinutes = 2, sourceIsUsdc = false),
            state.rowUi(now = 1_000, hiddenBalance = false),
        )
        assertEquals(146_001L, state.nextRowChange(now = 1_000))
        assertEquals(
            PerpsDepositRowUi.Delayed("a", PerpsDepositPendingStep.CONVERTING),
            state.rowUi(now = 146_001, hiddenBalance = false),
        )
        assertNull(state.nextRowChange(now = 146_001))
    }

    @Test
    fun `a USDC row knows its source is USDC`() {
        val state = state(deposit("a", step = PerpsDepositPendingStep.CONVERTING, symbol = "USDC"))

        assertEquals(
            PerpsDepositRowUi.Step("a", PerpsDepositPendingStep.CONVERTING, "10 USDC", etaMinutes = 2, sourceIsUsdc = true),
            state.rowUi(now = 1_000, hiddenBalance = false),
        )
    }

    @Test
    fun `hidden balances mask the amounts`() {
        val state = state(deposit("a"))
        assertEquals(
            PerpsDepositRowUi.Step("a", PerpsDepositPendingStep.SENDING, HIDDEN_BALANCE, etaMinutes = 2, sourceIsUsdc = false),
            state.rowUi(now = 1_000, hiddenBalance = true),
        )
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.COMPLETED, credited = "10")))
        assertEquals(
            PerpsDepositRowUi.Completed(setOf("a"), HIDDEN_BALANCE),
            state.rowUi(now = 1_000, hiddenBalance = true),
        )
    }

    @Test
    fun `an unconfirmed deposit is a problem of its own`() {
        val state = state(deposit("a"))
        state.apply(listOf(deposit("a", PerpsDepositStatus.State.UNCONFIRMED)))

        assertEquals(PerpsDepositRowUi.Problem("a", unconfirmed = true), state.rowUi(0, hiddenBalance = false))
    }

    private fun state(vararg deposits: PerpsDepositStatus) = PerpsDepositBannerState(isVisible = true).also {
        it.apply(deposits.toList())
    }

    private fun PerpsDepositBannerState.listedIds() = listedDeposits.mapTo(HashSet()) { it.id }

    private fun deposit(
        id: String,
        state: PerpsDepositStatus.State = PerpsDepositStatus.State.PENDING,
        step: PerpsDepositPendingStep? = PerpsDepositPendingStep.SENDING,
        createdAt: Long = 1_000,
        estimated: Long? = 120,
        credited: String? = null,
        expected: String? = "10",
        acknowledged: Boolean = false,
        symbol: String = "TON",
    ) = PerpsDepositStatus(
        id = id,
        createdAt = createdAt,
        sourceAmount = BigDecimal("10"),
        sourceSymbol = symbol,
        sourceNetwork = "TON",
        expectedAmount = expected?.let(::BigDecimal),
        creditedAmount = credited?.let(::BigDecimal),
        estimatedDurationSeconds = estimated,
        state = state,
        step = step,
        skipsConversion = false,
        transactions = emptySet(),
        acknowledged = acknowledged,
    )
}

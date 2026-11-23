package com.tonapps.perps.screens.depositstatus

import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.funding.PerpsDepositStatus
import com.tonapps.perps.funding.perpsTransactionHash
import com.tonapps.perps.funding.toDepositStatus
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsDepositStatusMappingTest {

    @Test
    fun `a missing backend step is not inferred from the local stage`() {
        val status = record(stage = PerpsDepositStage.REGISTERED_PENDING).toDepositStatus()

        assertEquals(PerpsDepositStatus.State.PENDING, status?.state)
        assertNull(status?.step)
    }

    @Test
    fun `an outcome keeps the step it ended at`() {
        val pending = record(stage = PerpsDepositStage.REGISTERED_PENDING, step = PerpsDepositPendingStep.CONVERTING)
        val refunded = pending.copy(stage = PerpsDepositStage.REFUNDED)
        val credited = pending.copy(stage = PerpsDepositStage.CREDITED, creditedAmount = "707.19")

        assertEquals(PerpsDepositPendingStep.CONVERTING, refunded.toDepositStatus()?.step)
        assertEquals(
            listOf(PerpsDepositStepState.DONE, PerpsDepositStepState.FAILED, PerpsDepositStepState.WAITING),
            refunded.toDepositStatus()?.toStatusUi(now = CREATED_AT, hiddenBalance = false)?.steps?.map { it.state },
        )
        assertEquals(
            List(3) { PerpsDepositStepState.DONE },
            credited.toDepositStatus()?.toStatusUi(now = CREATED_AT, hiddenBalance = false)?.steps?.map { it.state },
        )
        assertEquals(BigDecimal("707.19"), credited.toDepositStatus()?.creditedAmount)
    }

    @Test
    fun `only a deposit through the aggregator shows conversion`() {
        val direct = record(route = PerpsDepositRoute.DIRECT_GATEWAY, symbol = "USDC", execution = null).toDepositStatus()
        val bridge = record(symbol = "USDC", execution = "funxyz_42161_0xabc_5000000_1793200000000").toDepositStatus()
        val relayUsdc = record(symbol = "USDC").toDepositStatus()
        val relaySwap = record().toDepositStatus()

        assertEquals(true, direct?.skipsConversion)
        assertEquals(true, bridge?.skipsConversion)
        assertEquals(false, relayUsdc?.skipsConversion)
        assertEquals(true, relayUsdc?.sourceIsUsdc)
        assertEquals(false, relaySwap?.skipsConversion)
        assertEquals(false, relaySwap?.sourceIsUsdc)
    }

    @Test
    fun `USDC through the aggregator has a transfer step`() {
        val usdc = record(stage = PerpsDepositStage.REGISTERED_PENDING, symbol = "USDC", step = PerpsDepositPendingStep.CONVERTING)
            .toDepositStatus()
            ?.toStatusUi(now = CREATED_AT, hiddenBalance = false)

        assertEquals(PerpsDepositStepKind.entries, usdc?.steps?.map { it.kind })
        assertEquals(
            listOf(PerpsDepositStepState.DONE, PerpsDepositStepState.CURRENT, PerpsDepositStepState.WAITING),
            usdc?.steps?.map { it.state },
        )
        assertEquals(true, usdc?.sourceIsUsdc)
    }

    @Test
    fun `a conversion reported for a two step deposit moves on to adding`() {
        val bridge = record(
            stage = PerpsDepositStage.REGISTERED_PENDING,
            symbol = "USDC",
            step = PerpsDepositPendingStep.CONVERTING,
            execution = "funxyz_8453_0xabc_5000000_1793200000000",
        ).toDepositStatus()

        assertEquals(PerpsDepositPendingStep.ADDING, bridge?.step)
        assertEquals(
            listOf(PerpsDepositStepState.DONE, PerpsDepositStepState.CURRENT),
            bridge?.toStatusUi(now = CREATED_AT, hiddenBalance = false)?.steps?.map { it.state },
        )
    }

    @Test
    fun `a draft or a withdrawal is not a deposit status`() {
        assertNull(record(stage = PerpsDepositStage.PREPARING).toDepositStatus())
        assertNull(record(kind = PerpsFundingKind.WITHDRAW, stage = PerpsDepositStage.ACCEPTED).toDepositStatus())
    }

    @Test
    fun `outcomes map to their states`() {
        assertEquals(PerpsDepositStatus.State.COMPLETED, record(stage = PerpsDepositStage.CREDITED).toDepositStatus()?.state)
        assertEquals(PerpsDepositStatus.State.FAILED, record(stage = PerpsDepositStage.FAILED).toDepositStatus()?.state)
        assertEquals(PerpsDepositStatus.State.REFUNDED, record(stage = PerpsDepositStage.REFUNDED).toDepositStatus()?.state)
        assertEquals(
            PerpsDepositStatus.State.UNCONFIRMED,
            record(stage = PerpsDepositStage.UNCONFIRMED).toDepositStatus()?.state,
        )
        assertEquals(
            PerpsDepositStatus.State.PENDING,
            record(stage = PerpsDepositStage.NEEDS_AUTHORIZATION).toDepositStatus()?.state,
        )
    }

    @Test
    fun `amounts come from the journal and a missing estimate stays missing`() {
        val status = record(estimate = null).toDepositStatus()

        assertEquals(BigDecimal("350.000000000"), status?.sourceAmount)
        assertEquals(BigDecimal("707.190000"), status?.expectedAmount)
        assertEquals("TON", status?.sourceNetwork)
        assertNull(status?.estimatedDurationSeconds)
        assertEquals(setOf("source", "l1"), status?.transactions)
    }

    @Test
    fun `the network is named after the source chain`() {
        val usdc = record(symbol = "USDC").copy(sourceAssetId = "eth/mainnet/erc20/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val arbitrum = record(symbol = "ETH").copy(sourceAssetId = "arb/mainnet/coin")
        val usdtTron = record(symbol = "USDT").copy(sourceAssetId = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")

        assertEquals("Ethereum", usdc.toDepositStatus()?.sourceNetwork)
        assertEquals("Arbitrum", arbitrum.toDepositStatus()?.sourceNetwork)
        assertEquals("TRON", usdtTron.toDepositStatus()?.sourceNetwork)
    }

    @Test
    fun `history ids match the journal whatever their chain prefix`() {
        assertEquals("abc", perpsTransactionHash("ton:ABC"))
        assertEquals("def", perpsTransactionHash("ethereum:0xDEF"))
        assertEquals("def", perpsTransactionHash(" 0xdef "))
        assertNull(perpsTransactionHash("ton:"))
    }

    @Test
    fun `delay uses the estimate and nothing else`() {
        val status = record(estimate = 120).toDepositStatus()!!

        assertFalse(status.isDelayed(now = CREATED_AT + 120_000))
        assertTrue(status.isDelayed(now = CREATED_AT + 120_001))
    }

    @Test
    fun `the sheet shows every step and where the deposit is`() {
        val converting = status(step = PerpsDepositPendingStep.CONVERTING).toStatusUi(now = CREATED_AT, hiddenBalance = false)

        assertEquals(PerpsDepositStatusHeader.Eta(2), converting.header)
        assertEquals(
            listOf(
                PerpsDepositStepUi(PerpsDepositStepKind.SENDING, PerpsDepositStepState.DONE),
                PerpsDepositStepUi(PerpsDepositStepKind.CONVERTING, PerpsDepositStepState.CURRENT),
                PerpsDepositStepUi(PerpsDepositStepKind.ADDING, PerpsDepositStepState.WAITING),
            ),
            converting.steps,
        )
        assertEquals(formatUsdAmount(BigDecimal("707.19")), converting.receivedFormat)
        assertTrue(converting.approximate)
    }

    @Test
    fun `a delayed deposit keeps its step and says it takes longer`() {
        val delayed = status(step = PerpsDepositPendingStep.SENDING).toStatusUi(now = CREATED_AT + 200_000, hiddenBalance = false)

        assertEquals(PerpsDepositStatusHeader.Delayed, delayed.header)
        assertTrue(delayed.delayed)
        assertEquals(PerpsDepositStepState.CURRENT, delayed.steps.first().state)
    }

    @Test
    fun `a failure is shown on the step it happened at`() {
        val refunded = status(
            state = PerpsDepositStatus.State.REFUNDED,
            step = PerpsDepositPendingStep.CONVERTING,
        ).toStatusUi(now = CREATED_AT, hiddenBalance = false)

        assertEquals(PerpsDepositStatusHeader.Refunded, refunded.header)
        assertTrue(refunded.refunded)
        assertEquals(
            listOf(PerpsDepositStepState.DONE, PerpsDepositStepState.FAILED, PerpsDepositStepState.WAITING),
            refunded.steps.map { it.state },
        )
    }

    @Test
    fun `without a step the sheet shows one row`() {
        val pending = status(step = null).toStatusUi(now = CREATED_AT, hiddenBalance = false)
        val failed = status(state = PerpsDepositStatus.State.FAILED, step = null).toStatusUi(now = CREATED_AT, hiddenBalance = false)
        val completed = status(state = PerpsDepositStatus.State.COMPLETED, step = null, credited = "700")
            .toStatusUi(now = CREATED_AT, hiddenBalance = false)

        assertEquals(emptyList<PerpsDepositStepUi>(), pending.steps)
        assertTrue(pending.inProgress)
        assertEquals(emptyList<PerpsDepositStepUi>(), failed.steps)
        assertEquals(PerpsDepositStatusHeader.Failed, failed.header)
        assertEquals(List(3) { PerpsDepositStepState.DONE }, completed.steps.map { it.state })
        assertEquals(formatUsdAmount(BigDecimal("700")), completed.receivedFormat)
    }

    @Test
    fun `a USDC deposit has no conversion step`() {
        val usdc = status(step = PerpsDepositPendingStep.ADDING, skipsConversion = true)
            .toStatusUi(now = CREATED_AT, hiddenBalance = false)

        assertEquals(listOf(PerpsDepositStepKind.SENDING, PerpsDepositStepKind.ADDING), usdc.steps.map { it.kind })
        assertEquals(listOf(PerpsDepositStepState.DONE, PerpsDepositStepState.CURRENT), usdc.steps.map { it.state })
    }

    @Test
    fun `hidden balances mask the sheet and the list`() {
        val sheet = status().toStatusUi(now = CREATED_AT, hiddenBalance = true)
        val item = status(state = PerpsDepositStatus.State.COMPLETED, credited = "700").toListItemUi(hiddenBalance = true) { "" }

        assertEquals(HIDDEN_BALANCE, sheet.sourceFormat)
        assertEquals(HIDDEN_BALANCE, sheet.receivedFormat)
        assertFalse(sheet.approximate)
        assertEquals(HIDDEN_BALANCE, item.amountFormat)
        assertFalse(item.positive)
    }

    @Test
    fun `the list shows what arrived or is expected`() {
        val credited = status(state = PerpsDepositStatus.State.COMPLETED, credited = "99.8").toListItemUi(false) { "d$it" }
        val pending = status().toListItemUi(false) { "d$it" }
        val refunded = status(state = PerpsDepositStatus.State.REFUNDED).toListItemUi(false) { "d$it" }

        assertEquals(PerpsDepositListState.DEPOSITED, credited.state)
        assertEquals(formatSignedUsdAmount(BigDecimal("99.8")), credited.amountFormat)
        assertTrue(credited.positive)
        assertEquals(PerpsDepositListState.DEPOSITING, pending.state)
        assertEquals(formatUsdAmount(BigDecimal("707.19")), pending.amountFormat)
        assertFalse(pending.positive)
        assertEquals(PerpsDepositListState.FAILED, refunded.state)
        assertEquals("d$CREATED_AT", refunded.dateFormat)
    }

    private fun status(
        state: PerpsDepositStatus.State = PerpsDepositStatus.State.PENDING,
        step: PerpsDepositPendingStep? = PerpsDepositPendingStep.SENDING,
        credited: String? = null,
        skipsConversion: Boolean = false,
    ) = PerpsDepositStatus(
        id = "a",
        createdAt = CREATED_AT,
        sourceAmount = BigDecimal("350"),
        sourceSymbol = "GRAM",
        sourceNetwork = "TON",
        expectedAmount = BigDecimal("707.19"),
        creditedAmount = credited?.let(::BigDecimal),
        estimatedDurationSeconds = 146,
        state = state,
        step = step,
        skipsConversion = skipsConversion,
        transactions = emptySet(),
    )

    private fun record(
        kind: PerpsFundingKind = PerpsFundingKind.DEPOSIT,
        stage: PerpsDepositStage = PerpsDepositStage.SOURCE_SENDING,
        route: PerpsDepositRoute = PerpsDepositRoute.CROSSCHAIN_SWAP,
        symbol: String = "GRAM",
        step: PerpsDepositPendingStep? = null,
        estimate: Long? = 146,
        execution: String? = "0xexecution",
    ) = PerpsFundingRecord(
        operationId = "a",
        walletId = "wallet",
        kind = kind,
        stage = stage,
        route = route,
        beneficiary = "0xbeneficiary",
        sourceAssetId = "ton/mainnet/coin",
        sourceSymbol = symbol,
        sourceDecimals = 9,
        sourceAmount = "350000000000",
        targetAmount = "707190000",
        quoteId = null,
        executionId = execution,
        sourceTxHash = "0xSOURCE",
        l1TxHash = "0xL1",
        steps = emptyList(),
        linkSignature = null,
        linkDeadline = null,
        registration = null,
        depositId = null,
        creditedAmount = null,
        accountIndex = null,
        failureReason = null,
        pendingStep = step,
        estimatedDurationSeconds = estimate,
        revision = 0,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    private companion object {
        const val CREATED_AT = 1_000_000L
    }
}

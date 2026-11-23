package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.perps.data.PERPS_SLOW_POLL_MS
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsRealtimeTestEnv
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class PerpsFundingRecoveryTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv
    private lateinit var tracker: PerpsDepositTracker

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = "100000000")
        tracker = PerpsDepositTracker(env.journal, env.recovery, env.depositService, env.provider)
    }

    @Test
    fun aTronDepositWhoseBroadcastWentUnansweredIsFoundOnChainByItsTxid() = runTest {
        journalUnansweredTronDeposit()
        every { env.transactionLookup.canLookUp(any()) } returns true
        coEvery {
            env.transactionLookup.presence(WALLET_ID, TestAssets.trx.id, any(), "txid-deposit")
        } returns PerpsTransactionPresence.KNOWN

        recover()

        val step = env.journal.list(WALLET_ID).single().steps.single()
        assertEquals(PerpsFundingStepStatus.SENT, step.status)
        assertEquals("txid-deposit", step.txHash)
    }

    @Test
    fun aTronDepositThatRanOutOfEnergyOnChainFails() = runTest {
        journalUnansweredTronDeposit()
        every { env.transactionLookup.canLookUp(any()) } returns true
        coEvery {
            env.transactionLookup.presence(WALLET_ID, TestAssets.trx.id, any(), "txid-deposit")
        } returns PerpsTransactionPresence.REVERTED

        recover()

        assertEquals(PerpsFundingStepStatus.FAILED, env.journal.list(WALLET_ID).single().steps.single().status)
    }

    @Test
    fun aChainWithoutATransactionLookupIsLeftToTheBackendToRegister() = runTest {
        journalUnansweredTronDeposit()
        every { env.transactionLookup.canLookUp(any()) } returns false

        recover()

        assertEquals(PerpsDepositStage.REGISTRATION_PENDING, env.journal.list(WALLET_ID).single().stage)
        coVerify(exactly = 0) { env.transactionLookup.presence(any(), any(), any(), any()) }
    }

    @Test
    fun aRegisteredDepositWithRealtimeIsReadOnItsEventsInsteadOfEveryFewSeconds() = runTest {
        journal("registered", PerpsDepositStage.REGISTERED_PENDING)
        val registrar = pendingRegistrar()
        val realtime = PerpsRealtimeTestEnv(backgroundScope)
        realtime.connect(WALLET_ID)
        runCurrent()
        realtime.confirmSubscription(WALLET_ID)
        runCurrent()

        recover(registrar, realtime)
        coVerify(exactly = 1) { registrar.refresh(any()) }

        advanceTimeBy(POLL_DELAY_MS * 3)
        runCurrent()
        coVerify(exactly = 1) { registrar.refresh(any()) }

        realtime.publish(WALLET_ID, "perps.account", seq = 1)
        runCurrent()
        coVerify(exactly = 1) { registrar.refresh(any()) }

        realtime.publish(WALLET_ID, "perps.deposit", seq = 2)
        runCurrent()
        coVerify(exactly = 2) { registrar.refresh(any()) }

        advanceTimeBy(PERPS_SLOW_POLL_MS)
        runCurrent()
        coVerify(exactly = 3) { registrar.refresh(any()) }
    }

    @Test
    fun aRegisteredDepositWithoutRealtimeIsReadAtTheSlowPeriod() = runTest {
        journal("registered", PerpsDepositStage.REGISTERED_PENDING)
        val registrar = pendingRegistrar()

        recover(registrar, PerpsRealtimeTestEnv(backgroundScope))
        advanceTimeBy(PERPS_SLOW_POLL_MS - 1)
        runCurrent()
        coVerify(exactly = 1) { registrar.refresh(any()) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 2) { registrar.refresh(any()) }
    }

    @Test
    fun aDepositOrWithdrawalThatEndsWhileWatchedIsReportedAsANewOutcome() = runTest {
        journal("credited", PerpsDepositStage.CREDITED)
        journal("failing", PerpsDepositStage.AWAITING_DELIVERY)
        journal("refunding", PerpsDepositStage.REGISTERED_PENDING)
        journal("withdrawing", PerpsDepositStage.ACCEPTED, PerpsFundingKind.WITHDRAW)
        val outcomes = watchNewOutcomes()
        assertEquals(0, outcomes.size)

        moveTo("failing", PerpsDepositStage.FAILED)
        assertEquals(1, outcomes.size)

        moveTo("refunding", PerpsDepositStage.REFUNDED)
        assertEquals(2, outcomes.size)

        moveTo("withdrawing", PerpsDepositStage.AWAITING_L1)
        assertEquals(2, outcomes.size)

        moveTo("withdrawing", PerpsDepositStage.SETTLED)
        assertEquals(3, outcomes.size)
    }

    @Test
    fun dismissingEndedDepositsReportsNoNewOutcome() = runTest {
        journal("failed", PerpsDepositStage.FAILED)
        journal("refunded", PerpsDepositStage.REFUNDED)
        val outcomes = watchNewOutcomes()

        env.depositService.dismissOutcomes(WALLET_ID)
        runCurrent()

        assertEquals(emptyList<PerpsFundingRecord>(), env.journal.list(WALLET_ID))
        assertEquals(0, outcomes.size)
    }

    private fun TestScope.watchNewOutcomes(): List<Unit> {
        val outcomes = mutableListOf<Unit>()
        backgroundScope.launch { env.recovery.observeNewOutcomes(WALLET_ID).toList(outcomes) }
        runCurrent()
        return outcomes
    }

    private suspend fun TestScope.moveTo(operationId: String, stage: PerpsDepositStage) {
        val record = env.journal.list(WALLET_ID).single { it.operationId == operationId }
        env.journal.update(operationId, record.revision) { it.copy(stage = stage) }
        runCurrent()
    }

    private suspend fun journal(
        operationId: String,
        stage: PerpsDepositStage,
        kind: PerpsFundingKind = PerpsFundingKind.DEPOSIT,
    ) {
        env.journal.saveIfAbsent(
            PerpsFundingRecord(
                operationId = operationId,
                walletId = WALLET_ID,
                kind = kind,
                stage = stage,
                route = PerpsDepositRoute.CROSSCHAIN_SWAP,
                beneficiary = BENEFICIARY,
                sourceAssetId = TestAssets.trx.id,
                sourceSymbol = "TRX",
                sourceDecimals = TRX_DECIMALS,
                sourceAmount = TRX_AMOUNT.toString(),
                targetAmount = CREDITED.toString(),
                quoteId = null,
                executionId = null,
                sourceTxHash = null,
                l1TxHash = null,
                steps = emptyList(),
                linkSignature = null,
                linkDeadline = null,
                registration = null,
                depositId = null,
                creditedAmount = null,
                accountIndex = null,
                failureReason = null,
                revision = 0,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private suspend fun journalUnansweredTronDeposit() {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.tronPayload("deposit", to = TRON_ROUTER, data = env.tronNativeDepositData(), value = TRX_AMOUNT))
        }
        val source = env.source(TestAssets.trx.id)
        val request = env.depositService.confirmRequest(WALLET_ID, source, TRX_AMOUNT, PerpsSourceScreen.PerpsHome)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val account = source.account.data.chainAccount
        val operation = SignedOperation(
            deposit.operation.id,
            deposit.operation.payloads.map { SignedPayload.signed(it, "signed-${it.id}") },
        )
        tracker.onSigned(request, account, operation, mockk())
        try {
            tracker.track(request, account, operation) { throw IllegalStateException("connection reset") }
        } catch (_: IllegalStateException) {
        }
    }

    private fun pendingRegistrar(): PerpsDepositRegistrar = mockk {
        coEvery { refresh(any()) } answers { firstArg() }
    }

    private fun TestScope.recover(registrar: PerpsDepositRegistrar, realtime: PerpsRealtimeTestEnv) {
        PerpsFundingRecovery(
            journal = env.journal,
            registrar = registrar,
            repository = env.repository,
            transactionLookup = env.transactionLookup,
            accountRefresh = PerpsAccountRefresh(),
            unifiedAccountRepository = mockk(),
            withdrawTracker = env.tracker,
            realtime = realtime.realtime,
            scope = backgroundScope,
        ).refresh(WALLET_ID)
        runCurrent()
    }

    private fun TestScope.recover() {
        PerpsFundingRecovery(
            journal = env.journal,
            registrar = mockk(),
            repository = env.repository,
            transactionLookup = env.transactionLookup,
            accountRefresh = PerpsAccountRefresh(),
            unifiedAccountRepository = mockk(),
            withdrawTracker = env.tracker,
            realtime = PerpsRealtimeTestEnv(backgroundScope).realtime,
            scope = backgroundScope,
        ).refresh(WALLET_ID)
        runCurrent()
    }

    private companion object {
        val CREDITED: BigInteger = BigInteger.valueOf(1_200_000L)
        val TRX_AMOUNT: BigInteger = BigInteger.valueOf(4_360_000L)
        const val TRON_ROUTER = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val TRX_DECIMALS = 6
        const val BENEFICIARY = "0x00000000000000000000000000000000000000b1"
        const val POLL_DELAY_MS = 5_000L
    }
}

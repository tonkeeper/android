package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
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

    private suspend fun journalUnansweredTronDeposit() {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.tronPayload("deposit", to = TRON_ROUTER, data = CALL_DATA, value = TRX_AMOUNT))
        }
        val source = env.source(TestAssets.trx.id)
        val request = env.depositService.confirmRequest(WALLET_ID, source, TRX_AMOUNT)
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

    private fun TestScope.recover() {
        PerpsFundingRecovery(
            journal = env.journal,
            registrar = mockk(),
            repository = env.repository,
            transactionLookup = env.transactionLookup,
            accountRefresh = PerpsAccountRefresh(),
            unifiedAccountRepository = mockk(),
            withdrawTracker = env.tracker,
            scope = backgroundScope,
        ).refresh(WALLET_ID)
        runCurrent()
    }

    private companion object {
        val CREDITED: BigInteger = BigInteger.valueOf(1_200_000L)
        val TRX_AMOUNT: BigInteger = BigInteger.valueOf(4_360_000L)
        const val TRON_ROUTER = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val CALL_DATA = "49290c1c"
    }
}

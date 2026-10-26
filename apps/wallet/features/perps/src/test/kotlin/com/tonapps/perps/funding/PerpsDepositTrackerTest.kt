package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_OPERATION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class PerpsDepositTrackerTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv
    private lateinit var tracker: PerpsDepositTracker

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = "100000000")
        env.accounts[TestAssets.usdtTron.id] = testAccount(TestAssets.usdtTron, balance = "100000000")
        env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = "1000000")
        tracker = PerpsDepositTracker(env.journal, env.recovery, env.depositService, env.provider)
    }

    @Test
    fun aTronTokenDepositIsJournaledAsItsApprovalAndItsCall() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(
                env.tronPayload("approve", to = TRON_USDT, data = APPROVE_DATA),
                env.tronPayload("call", to = TRON_ROUTER, data = CALL_DATA),
            )
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.usdtTron.id), TRON_AMOUNT)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.usdtTron.id).account.data.chainAccount, signed(deposit), mockk<CryptoWallet>())

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositRoute.CROSSCHAIN_SWAP, record.route)
        assertEquals(PerpsDepositStage.SOURCE_SIGNED, record.stage)
        assertEquals(listOf(PerpsFundingStepKind.APPROVAL, PerpsFundingStepKind.MAIN), record.steps.map { it.kind })
        assertEquals(listOf("tron/mainnet", "tron/mainnet"), record.steps.map { it.chainId })
        assertEquals(listOf(TRON_USDT, TRON_ROUTER), record.steps.map { it.to })
        assertEquals(listOf(APPROVE_DATA, CALL_DATA), record.steps.map { it.data })
        assertEquals(listOf("txid-approve", "txid-call"), record.steps.map { it.txHash })
    }

    @Test
    fun aBitcoinDepositIsJournaledAsItsPrebuiltTransaction() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.bitcoinPayload("pay", psbt = PSBT))
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.btc.id), BTC_AMOUNT)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.btc.id).account.data.chainAccount, signed(deposit), mockk<CryptoWallet>())

        val step = env.journal.list(WALLET_ID).single().steps.single()
        assertEquals(PerpsFundingStepKind.MAIN, step.kind)
        assertEquals("btc/mainnet", step.chainId)
        assertEquals(PSBT, step.data)
        assertEquals("0xsigned-pay", step.signedData)
        assertEquals("txid-pay", step.txHash)
    }

    @Test
    fun aTronBroadcastTheNodeRefusedFailsTheDeposit() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.tronPayload("call", to = TRON_ROUTER, data = CALL_DATA))
        }

        send(TestAssets.usdtTron.id, TRON_AMOUNT, NodeError.NetworkWrap(NetworkError(0, "CONTRACT_VALIDATE_ERROR")))

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.FAILED, record.stage)
        assertEquals(PerpsFundingStepStatus.FAILED, record.steps.single().status)
    }

    @Test
    fun aBitcoinBroadcastConflictingWithTheMempoolFailsTheDeposit() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.bitcoinPayload("pay", psbt = PSBT))
        }

        send(TestAssets.btc.id, BTC_AMOUNT, NodeError.NetworkWrap(ChainError.BitcoinMemPoolConflict("Previous transaction is pending")))

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.FAILED, record.stage)
        assertEquals(PerpsFundingStepStatus.FAILED, record.steps.single().status)
    }

    @Test
    fun aBitcoinBroadcastWhoseAnswerWasLostStaysUnknown() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.bitcoinPayload("pay", psbt = PSBT))
        }

        send(TestAssets.btc.id, BTC_AMOUNT, NodeError.NetworkWrap(NetworkError(502, "Bad Gateway")))

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, record.stage)
        assertEquals(PerpsFundingStepStatus.UNKNOWN, record.steps.single().status)
    }

    @Test
    fun theTronRefusalCodeOffTronStaysUnknown() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.bitcoinPayload("pay", psbt = PSBT))
        }

        send(TestAssets.btc.id, BTC_AMOUNT, NodeError.NetworkWrap(NetworkError(0, "no status")))

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, record.stage)
        assertEquals(PerpsFundingStepStatus.UNKNOWN, record.steps.single().status)
    }

    private suspend fun send(assetId: String, amount: BigInteger, error: NodeError) {
        val source = env.source(assetId)
        val request = env.depositService.confirmRequest(WALLET_ID, source, amount)
        val account = source.account.data.chainAccount
        val operation = signed(request.type as ConfirmType.PerpetualDeposit)
        tracker.onSigned(request, account, operation, mockk<CryptoWallet>())
        try {
            tracker.track(request, account, operation) { throw error }
        } catch (_: NodeError) {
        }
    }

    private fun signed(deposit: ConfirmType.PerpetualDeposit): SignedOperation {
        assertEquals(DEPOSIT_OPERATION_ID, deposit.operation.id)
        return SignedOperation(
            deposit.operation.id,
            deposit.operation.payloads.map { SignedPayload.signed(it, "0xsigned-${it.id}") },
        )
    }

    private companion object {
        val CREDITED: BigInteger = BigInteger.valueOf(24_000_000L)
        val TRON_AMOUNT: BigInteger = BigInteger.valueOf(25_000_000L)
        val BTC_AMOUNT: BigInteger = BigInteger.valueOf(99_707L)
        const val TRON_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TRON_ROUTER = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val APPROVE_DATA = "095ea7b3000000000000000000000000f0623e1012177482912fb057e44e1a9769b1f5880000000000000000000000000000000000000000000000000000000017d78400"
        const val CALL_DATA = "deadbeef"
        const val PSBT = "cHNidP8BAL4CAAAAAeN/InZ+84d+5IKR6rs1LnheNRl8u0hnj797qsk2CBlHEwAAAAD9////A3uFAQAAAAAAFgAUFtc3gKrpMyWq" +
            "4UpZ+y8B2QnmMg1F7WoAAAAAABYAFNxr+GNUEF3i/NmGiisDdtZzHLkvAAAAAAAAAABEakIweGZiYTQyNDY0MDBjYzEwZGIyZDQ5YjA5" +
            "YmFiZTJmNGNjZGIwNTE2MjRjYmZjMWVlZDMzMTA3NDg2YWExZWYyNjkAAAAAAAEBH013bAAAAAAAFgAU3Gv4Y1QQXeL82YaKKwN21nMc" +
            "uS8AAAAA"
    }
}

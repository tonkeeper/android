package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_OPERATION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GATEWAY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigInteger

class PerpsDepositTrackerTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv
    private lateinit var tracker: PerpsDepositTracker
    private var relayedAssetId: String = USDC.id

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = "100000000")
        env.accounts[TestAssets.usdtTron.id] = testAccount(TestAssets.usdtTron, balance = "100000000")
        env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = "1000000")
        env.accounts[TestAssets.usdtEth.id] = testAccount(TestAssets.usdtEth, balance = "100000000")
        env.bitcoinFeeRate(BITCOIN_FEE_RATE)
        tracker = PerpsDepositTracker(env.journal, env.recovery, env.depositService, env.provider)
    }

    @Test
    fun aTronTokenDepositIsJournaledAsItsApprovalAndItsCall() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(
                env.tronPayload("approve", to = TRON_USDT, data = env.tronApproveData(TRON_ROUTER, TRON_AMOUNT)),
                env.tronPayload("call", to = TRON_ROUTER, data = tronDepositData()),
            )
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.usdtTron.id), TRON_AMOUNT, PerpsSourceScreen.PerpsHome)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.usdtTron.id).account.data.chainAccount, signed(deposit), mockk<MnemonicWallet>())

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositRoute.CROSSCHAIN_SWAP, record.route)
        assertEquals(PerpsDepositStage.SOURCE_SIGNED, record.stage)
        assertEquals(listOf(PerpsFundingStepKind.APPROVAL, PerpsFundingStepKind.MAIN), record.steps.map { it.kind })
        assertEquals(listOf("tron/mainnet", "tron/mainnet"), record.steps.map { it.chainId })
        assertEquals(listOf(TRON_USDT, TRON_ROUTER), record.steps.map { it.to })
        assertEquals(listOf(env.tronApproveData(TRON_ROUTER, TRON_AMOUNT), tronDepositData()), record.steps.map { it.data })
        assertEquals(listOf("txid-approve", "txid-call"), record.steps.map { it.txHash })
    }

    @Test
    fun aTokenDepositIsJournaledUnderTheTokenNotTheChainCoin() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.tronPayload("call", to = TRON_ROUTER, data = tronDepositData()))
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.usdtTron.id), TRON_AMOUNT, PerpsSourceScreen.PerpsHome)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.usdtTron.id).account.data.chainAccount, signed(deposit), mockk<MnemonicWallet>())

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(TestAssets.usdtTron.symbol, record.sourceSymbol)
        assertEquals(TestAssets.usdtTron.decimals, record.sourceDecimals)
    }

    @Test
    fun theQuotedArrivalTimeIsJournaledForTheStatusRow() = runTest {
        env.quoteDeposits(
            provider = "relay",
            credited = CREDITED,
            executionId = DEPOSIT_EXECUTION_ID,
            estimatedDurationSeconds = ARRIVAL_SECONDS,
        ) {
            listOf(env.tronPayload("call", to = TRON_ROUTER, data = tronDepositData()))
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.usdtTron.id), TRON_AMOUNT, PerpsSourceScreen.PerpsHome)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.usdtTron.id).account.data.chainAccount, signed(deposit), mockk<MnemonicWallet>())

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(ARRIVAL_SECONDS.toLong(), record.estimatedDurationSeconds)
        assertNull(record.pendingStep)
    }

    @Test
    fun aBitcoinDepositIsJournaledAsItsPrebuiltTransaction() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.bitcoinPayload("pay", psbt = PSBT))
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.btc.id), BTC_AMOUNT, PerpsSourceScreen.PerpsHome)
        val deposit = request.type as ConfirmType.PerpetualDeposit

        tracker.onSigned(request, env.source(TestAssets.btc.id).account.data.chainAccount, signed(deposit), mockk<MnemonicWallet>())

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
            listOf(env.tronPayload("call", to = TRON_ROUTER, data = tronDepositData()))
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
    fun aSendTheNodeSaysUsedItsNonceStaysUnknown() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.tronPayload("call", to = TRON_ROUTER, data = tronDepositData()))
        }

        // The nonce may be this step's own transaction, mined behind a lagging lookup: recovery looks it up.
        send(TestAssets.usdtTron.id, TRON_AMOUNT, NodeError.NonceTaken("nonce too low"))

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

    @Test
    fun aRelayedDepositIsJournaledBeforeTheRelayerIsAsked() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)

        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(DEPOSIT_OPERATION_ID, record.operationId)
        assertEquals(PerpsDepositStage.SOURCE_SIGNED, record.stage)
        val step = record.steps.single()
        assertEquals(PerpsFundingStepKind.MAIN, step.kind)
        assertEquals(PerpsFundingStepStatus.SIGNED, step.status)
        val quoted = (request.type as ConfirmType.PerpetualDeposit).operation.payloads.single().meta as PayloadMeta.Evm.Main
        assertEquals(quoted.data, step.data)
        assertNull(step.txHash)
    }

    @Test
    fun aCrossChainDepositTheRelayerSentKeepsItsHashAndAwaitsRegistration() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        val hash = tracker.trackRelayed(request, usdcAccount()) { RELAYER_HASH }

        assertEquals(RELAYER_HASH, hash)
        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.REGISTRATION_PENDING, record.stage)
        assertEquals(RELAYER_HASH, record.sourceTxHash)
        assertEquals(PerpsFundingStepStatus.SENT, record.steps.single().status)
        assertEquals(RELAYER_HASH, record.steps.single().txHash)
    }

    @Test
    fun aDirectDepositTheRelayerSentIsRegisteredWithTheRelayerTransaction() = runTest {
        val request = quoteRelayedUsdc(provider = "direct", executionId = null)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        tracker.trackRelayed(request, usdcAccount()) { RELAYER_HASH }

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositRoute.DIRECT_GATEWAY, record.route)
        assertEquals(PerpsDepositStage.REGISTRATION_PENDING, record.stage)
        assertEquals(RELAYER_HASH, record.l1TxHash)
    }

    @Test
    fun aCrossChainDepositTheRelayerSentWithoutAHashAwaitsRegistration() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        tracker.trackRelayed(request, usdcAccount()) { null }

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.REGISTRATION_PENDING, record.stage)
        assertEquals(PerpsFundingStepStatus.SENT, record.steps.single().status)
        assertNull(record.sourceTxHash)
    }

    @Test
    fun aRelayerThatLostTheAnswerLeavesTheDepositUnknown() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        try {
            tracker.trackRelayed(request, usdcAccount()) { throw RelayerOutcomeUnknownException(IOException("timeout")) }
        } catch (_: RelayerOutcomeUnknownException) {
        }

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, record.stage)
        assertEquals(PerpsFundingStepStatus.UNKNOWN, record.steps.single().status)
    }

    @Test
    fun aRelayerRejectionThatMayHaveSentLeavesTheDepositUnknown() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        try {
            tracker.trackRelayed(request, usdcAccount()) { throw RelayerRejectedException(reason = null) }
        } catch (_: RelayerRejectedException) {
        }

        val record = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, record.stage)
        assertEquals(PerpsFundingStepStatus.UNKNOWN, record.steps.single().status)
    }

    @Test
    fun aRelayerRejectionThatSentNothingLeavesNoDeposit() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        try {
            tracker.trackRelayed(request, usdcAccount()) {
                throw RelayerRejectedException(reason = "rejected", nothingSent = true)
            }
        } catch (_: RelayerRejectedException) {
        }

        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aRelayerThatRefusedTheSendLeavesNoDeposit() = runTest {
        val request = quoteRelayedUsdc(provider = "relay", executionId = DEPOSIT_EXECUTION_ID)
        tracker.onRelayed(request, usdcAccount(), mockk<MnemonicWallet>())

        try {
            tracker.trackRelayed(request, usdcAccount()) { throw IllegalStateException("relayer failed") }
        } catch (_: IllegalStateException) {
        }

        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    private suspend fun quoteRelayedUsdc(provider: String, executionId: String?): ConfirmRequest {
        val direct = provider == "direct"
        relayedAssetId = if (direct) {
            USDC.id
        } else {
            TestAssets.usdtEth.id
        }
        val token = relayedAssetId.substringAfterLast('/')
        val (spender, payment) = if (direct) {
            GATEWAY to env.gatewayDepositData(L1_ADDRESS, USDC_AMOUNT)
        } else {
            RELAY_DEPOSITORY to env.tokenDepositData(token, USDC_AMOUNT)
        }
        val batch = env.batchData(
            Triple(token, BigInteger.ZERO, env.approveData(spender, USDC_AMOUNT)),
            Triple(spender, BigInteger.ZERO, payment),
        )
        env.quoteDeposits(provider = provider, credited = CREDITED, executionId = executionId) {
            listOf(env.evmPayload("execute", to = TestWallet.ETH_ADDRESS, data = batch))
        }
        return env.depositService.confirmRequest(WALLET_ID, env.source(relayedAssetId), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)
    }

    private fun usdcAccount() = env.source(relayedAssetId).account.data.chainAccount

    private fun tronDepositData(): String = env.tronTokenDepositData(TRON_USDT, TRON_AMOUNT)

    private suspend fun send(assetId: String, amount: BigInteger, error: NodeError) {
        val source = env.source(assetId)
        val request = env.depositService.confirmRequest(WALLET_ID, source, amount, PerpsSourceScreen.PerpsHome)
        val account = source.account.data.chainAccount
        val operation = signed(request.type as ConfirmType.PerpetualDeposit)
        tracker.onSigned(request, account, operation, mockk<MnemonicWallet>())
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
        const val BITCOIN_FEE_RATE = 3L
        val USDC_AMOUNT: BigInteger = BigInteger.valueOf(25_000_000L)
        const val RELAYER_HASH = "0x5e1a7ed"
        const val TRON_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TRON_ROUTER = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val ARRIVAL_SECONDS = 146
        val PSBT: String = TestPsbt.deposit(99_707L)
    }
}

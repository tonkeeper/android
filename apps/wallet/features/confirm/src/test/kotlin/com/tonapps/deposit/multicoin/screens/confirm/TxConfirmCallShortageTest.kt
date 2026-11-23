package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A dApp's contract call (an Earn withdrawal, a WalletConnect `eth_sendTransaction`) whose fee the
 * chain's coin can't pay: the shortage is known on the confirm screen, before the passcode.
 */
class TxConfirmCallShortageTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val eth by lazy { testAccount(TestAssets.eth, balance = "2380000000000") }

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } answers {
            eth.takeIf { secondArg<String>() == TestAssets.eth.id }
        }
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.ok(NATIVE_FEE)
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun aCallTheCoinCannotPayForIsShortBeforeThePasscode() {
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            NodeRes.ok(
                GasReserveResult(thirdArg(), isAmountAdjusted = false, GasReserveResult.Error.InsufficientBalance),
            )
        }

        val (feature, shown) = open()
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertFalse("the fee stays on screen to compare with the balance", shown.last()!!.isFeeUnavailable)
        coVerify(exactly = 1) {
            provider.calculateGasReserve(eth.value, eth.value, CALL_VALUE, false, NATIVE_FEE, GasReservePolicy.DrainOrError)
        }
    }

    @Test
    fun aFailedBalanceCheckBlocksTheCallWithARetry() {
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } returns
            NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))

        val (feature, _) = open()
        scheduler.runCurrent()

        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)
    }

    private fun open(): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.eth.id, walletId = TestWallet.ID),
                type = ConfirmType.Call(
                    operation = ConfirmOperations.call(
                        chain = Chain.Ethereum.Mainnet,
                        contract = TestWallet.ETH_ADDRESS,
                        data = "0x2e1a7d4d",
                        amount = CALL_VALUE,
                    ),
                ),
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            walletUnlocker = mockk(),
            signatureGate = SignatureGate { false },
            confirmResults = mockk(relaxed = true),
            feeBuilder = mockk(),
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
            ledgerSigner = mockk(),
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        return feature to shown
    }

    private companion object {
        val CALL_VALUE: BigInteger = BigInteger.parseString("1000000000000")
        val NATIVE_FEE: Fee = Fee.Value(TestAssets.eth.value.chain.toAsset(), BigInteger.parseString("5140000000000"))
    }
}

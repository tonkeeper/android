package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TxConfirmFeeUnavailableTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val ton by lazy { testAccount(TestAssets.ton, balance = "1210000000") }

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns ton
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.InternalError("emulate failed"))
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            NodeRes.ok(GasReserveResult(amount = thirdArg(), isAmountAdjusted = false, error = null))
        }
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(energy = secondArg(), estimated = arg(6))
        }
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun aFailedEstimateLeavesTheTransferWithoutAFeeAndKeepsTheReason() {
        val (feature, shown) = open()
        scheduler.runCurrent()

        assertTrue(shown.last()!!.isFeeUnavailable)
        assertEquals(ConfirmationError.InternalError, feature.confirmationError.value)
    }

    @Test
    fun aRetryAfterTheNodeRecoversBringsTheFeeBack() {
        val (feature, shown) = open()
        scheduler.runCurrent()

        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.ok(NATIVE_FEE)
        feature.retry()
        scheduler.runCurrent()

        assertFalse(shown.last()!!.isFeeUnavailable)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aRelayedMethodKeepsTheFeeAvailable() {
        val battery = TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(100), rate = null),
            fee = Fee.Value(Asset.Battery(Chain.Ton.Mainnet), BigInteger.fromInt(5)),
        )
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(energy = secondArg(), estimated = arg(6), options = listOf(battery))
        }

        val (_, shown) = open()
        scheduler.runCurrent()

        assertFalse(shown.last()!!.isFeeUnavailable)
    }

    private fun open(): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.ton.id, walletId = TestWallet.ID),
                type = ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(
                        chain = Chain.Ton.Mainnet,
                        to = TestWallet.TON_ADDRESS,
                        amount = BigInteger.fromInt(1_000_000),
                    ),
                ),
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            walletUnlocker = mockk(),
            signatureGate = SignatureGate { false },
            confirmResults = mockk(relaxed = true),
            feeBuilder = feeBuilder,
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
            ledgerSigner = mockk(relaxed = true),
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        return feature to shown
    }

    private companion object {
        val NATIVE_FEE: Fee = Fee.Value(TestAssets.ton.value.chain.toAsset(), BigInteger.parseString("2800000"))
    }
}

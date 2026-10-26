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
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TxConfirmMaxCoinTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val passcodeManager = mockk<PasscodeManager>()
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val eth by lazy { testAccount(TestAssets.eth, balance = BALANCE.toString()) }

    private val battery by lazy {
        TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(100), rate = null),
            fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), BigInteger.ONE),
        )
    }
    private val native by lazy { TxFee(FeeAccount.Chain(eth), NATIVE_FEE) }

    private var options: List<TxFee> = emptyList()
    private val builtAmounts = CopyOnWriteArrayList<BigInteger>()
    private val builtShrinkToFit = CopyOnWriteArrayList<Boolean>()
    private val signed = CopyOnWriteArrayList<Transaction>()

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns eth
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.ok(NATIVE_FEE)
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            NodeRes.ok(GasReserveResult(amount = SHRUNK, isAmountAdjusted = true, error = null))
        }
        coEvery { provider.signTransaction(any(), any(), any(), any()) } answers {
            signed += firstArg<Transaction>()
            SignRes.err(SignError.InternalError("not broadcast in tests"))
        }
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            builtAmounts += arg<BigInteger>(3)
            builtShrinkToFit += arg<Boolean>(7)
            TxFeeState(energy = secondArg(), estimated = arg(6), options = options)
        }
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } returns mockk()
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun theBatteryIsOfferedTheWholeBalance() {
        options = listOf(battery, native)
        val (_, shown) = open()
        scheduler.runCurrent()

        val signing = shown.last()!!.signing as Signing.Tx
        assertEquals(listOf(BALANCE), builtAmounts.toList())
        assertEquals(listOf(true), builtShrinkToFit.toList())
        assertEquals(BALANCE, signing.sentWith(battery).amount)
    }

    @Test
    fun theCoinPayingItsOwnFeeSendsTheBalanceLessTheGas() {
        options = listOf(battery, native)
        val (_, shown) = open()
        scheduler.runCurrent()

        val signing = shown.last()!!.signing as Signing.Tx
        assertEquals(SHRUNK, signing.sentWith(native).amount)
    }

    @Test
    fun pickingTheCoinSignsTheShrunkAmount() {
        options = listOf(battery, native)
        val (feature, shown) = open()
        scheduler.runCurrent()

        feature.selectFee(native)
        scheduler.runCurrent()
        feature.sendTransaction(context, shown.last()!!)
        scheduler.runCurrent()

        assertEquals(listOf(SHRUNK), signed.map { it.amount })
        coVerify(exactly = 1) { provider.signTransaction(any(), NATIVE_FEE, any(), any()) }
    }

    @Test
    fun withoutARelayedMethodTheMaxStaysShrunk() {
        options = emptyList()
        val (_, shown) = open()
        scheduler.runCurrent()

        val signing = shown.last()!!.signing as Signing.Tx
        assertEquals(SHRUNK, signing.value.amount)
        assertNull(signing.walletPaid)
    }

    @Test
    fun aTypedAmountIsNotShrunkToFit() {
        options = listOf(battery, native)
        open(isMax = false, amount = SHRUNK)
        scheduler.runCurrent()

        assertEquals(listOf(SHRUNK), builtAmounts.toList())
        assertEquals(listOf(false), builtShrinkToFit.toList())
    }

    @Test
    fun aFailedReservationLeavesTheCoinPricedAgainstTheWholeBalance() {
        options = listOf(battery, native)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } returns
            NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))
        val (_, shown) = open()
        scheduler.runCurrent()

        val signing = shown.last()!!.signing as Signing.Tx
        assertEquals(listOf(false), builtShrinkToFit.toList())
        assertEquals(BALANCE, signing.sentWith(native).amount)
    }

    private fun open(
        isMax: Boolean = true,
        amount: BigInteger = BALANCE,
    ): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.eth.id, walletId = TestWallet.ID),
                type = ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(
                        chain = Chain.Ethereum.Mainnet,
                        to = TestWallet.ETH_ADDRESS,
                        amount = amount,
                    ),
                    isMax = isMax,
                ),
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            passcodeManager = passcodeManager,
            confirmResults = mockk(relaxed = true),
            feeBuilder = feeBuilder,
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        return feature to shown
    }

    private companion object {
        val BALANCE: BigInteger = BigInteger.parseString("390854407154095")
        val NATIVE_FEE: Fee = Fee.Value(TestAssets.eth.value.chain.toAsset(), BigInteger.parseString("192483392488"))
        val SHRUNK: BigInteger = BALANCE - NATIVE_FEE.amount
    }
}

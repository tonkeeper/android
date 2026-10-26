package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuote
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSend
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A transfer whose relayed fee methods are only estimates until one is selected: the selected one is
 * priced for real before the transaction is shown, and again for every other pick.
 */
class TxConfirmFeeQuoteTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val passcodeManager = mockk<PasscodeManager>()
    private val confirmResults = mockk<ConfirmResultListener>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val eth by lazy { testAccount(TestAssets.eth, balance = "795000000000000") }
    private val usdt by lazy { testAccount(TestAssets.usdtEth, balance = "2006121") }

    private val battery by lazy {
        TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(100), rate = null),
            fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), BigInteger.fromInt(18)),
        )
    }
    private val native by lazy { TxFee(FeeAccount.Chain(eth), NATIVE_FEE) }
    private val token by lazy {
        TxFee(FeeAccount.Chain(usdt), Fee.Value(usdt.asset.value, BigInteger.fromInt(115_304)))
    }

    private val quoted = CopyOnWriteArrayList<String>()
    private val relayed = CopyOnWriteArrayList<String>()
    private var quoteLatencyMs = 0L
    private var relayerAnswer: () -> Unit = {}

    private val quote = RelayerQuote { state, option ->
        delay(quoteLatencyMs)
        quoted += option.id
        val priced = option.copy(fee = Fee.Value(option.fee.asset, option.fee.amount + BigInteger.ONE))
        state.copy(
            options = state.options.map { listed ->
                if (listed.id == priced.id) {
                    priced
                } else {
                    listed
                }
            },
            relayer = RelayerSend { _, selected ->
                relayed += selected.id
                relayerAnswer()
            },
            quotedId = priced.id,
        )
    }

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } answers {
            when (secondArg<String>()) {
                TestAssets.eth.id -> eth
                TestAssets.usdtEth.id -> usdt
                else -> null
            }
        }
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.ok(NATIVE_FEE)
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            NodeRes.ok(GasReserveResult(amount = thirdArg(), isAmountAdjusted = false, error = null))
        }
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(
                energy = secondArg(),
                estimated = arg(6),
                options = listOf(battery, native, token),
                quote = quote,
            )
        }
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } returns mockk()
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun theSelectedMethodIsPricedBeforeTheTransactionIsShown() {
        val (feature, shown) = open()
        scheduler.runCurrent()

        val fee = shown.last()!!.fee
        assertEquals(listOf(Asset.Battery.ID), quoted.toList())
        assertEquals(Asset.Battery.ID, fee.quotedId)
        assertEquals("19", fee.resolve(null)?.fee?.amount.toString())
        assertEquals("115304", fee.findOption(token.id)?.fee?.amount.toString())
        assertFalse(feature.feeQuoting.value)
    }

    @Test
    fun nothingIsShownWhileTheFirstQuoteIsInFlight() {
        quoteLatencyMs = 1_000
        val (feature, shown) = open()
        scheduler.runCurrent()

        assertTrue(shown.all { it == null })
        assertTrue(feature.feeQuoting.value)

        scheduler.advanceTimeBy(1_001)

        assertEquals(Asset.Battery.ID, shown.last()?.fee?.quotedId)
        assertFalse(feature.feeQuoting.value)
    }

    @Test
    fun anotherPickIsPricedFromTheEstimateAgain() {
        val (feature, shown) = open()
        scheduler.runCurrent()

        feature.selectFee(token)
        scheduler.runCurrent()

        val fee = shown.last()!!.fee
        assertEquals(listOf(Asset.Battery.ID, token.id), quoted.toList())
        assertEquals(token.id, fee.quotedId)
        assertEquals("115305", fee.resolve(token.id)?.fee?.amount.toString())
        assertEquals("18", fee.findOption(Asset.Battery.ID)?.fee?.amount.toString())
    }

    @Test
    fun pickingTheCoinPricesNothing() {
        val (feature, shown) = open()
        scheduler.runCurrent()

        feature.selectFee(native)
        scheduler.runCurrent()

        val fee = shown.last()!!.fee
        assertEquals(listOf(Asset.Battery.ID), quoted.toList())
        assertNull(fee.quotedId)
        assertNull(fee.relayer)
    }

    @Test
    fun aChainErrorStaysHiddenWhileARelayedMethodCanPay() {
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.InsufficientFunds("no gas"))
        offer(battery)

        val (feature, shown) = open()
        scheduler.runCurrent()

        assertEquals(Asset.Battery.ID, shown.last()?.fee?.quotedId)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aChainErrorIsShownOnceNoMethodCanPay() {
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.InsufficientFunds("no gas"))
        offer(battery.copy(available = false), token.copy(available = false))

        val (feature, shown) = open()
        scheduler.runCurrent()

        assertTrue(quoted.isEmpty())
        assertEquals(2, shown.last()?.fee?.options?.size)
        assertNotNull(feature.confirmationError.value)
    }

    /**
     * Nothing can pay, and the selection lands on the empty battery: the shortage is reported all the
     * same, so the dialog offers to recharge it or deposit ETH straight away, instead of leaving a
     * disabled slider with the recharge inside the picker.
     */
    @Test
    fun aShortageIsShownWhenNoMethodCanPayEvenWithTheBatterySelected() {
        val emptyBattery = battery.copy(account = FeeAccount.Keeper(balance = BigInteger.ZERO, rate = null))
        val poorNative = TxFee(FeeAccount.Chain(testAccount(TestAssets.eth, balance = "0")), NATIVE_FEE)
        offer(emptyBattery, poorNative)

        val (feature, shown) = open()
        scheduler.runCurrent()

        val pending = shown.last()!!
        assertEquals(Asset.Battery.ID, pending.fee.resolve(feature.pickedFeeId.value)?.id)
        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertTrue("the dialog offers the recharge", pending.isShortOnFeeOnly())
    }

    @Test
    fun aQuoteThatExpiredUnlocksTheScreenAndARetryPricesAgain() {
        relayerAnswer = { throw RelayerQuoteExpiredException() }
        val (feature, shown) = open()
        scheduler.runCurrent()

        feature.sendTransaction(context, shown.last()!!)
        scheduler.runCurrent()

        assertEquals(listOf(Asset.Battery.ID), relayed.toList())
        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)

        relayerAnswer = {}
        feature.retry(context)
        scheduler.runCurrent()

        assertNull(feature.confirmationError.value)
        assertEquals(listOf(Asset.Battery.ID, Asset.Battery.ID), quoted.toList())
        coVerify(exactly = 2) { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun aSendThatFailedAfterThePasscodeIsAnsweredAsFailed() {
        relayerAnswer = { throw IllegalStateException("relayer is down") }
        val (feature, shown) = open(context = DAPP)
        scheduler.runCurrent()

        feature.sendTransaction(context, shown.last()!!)
        scheduler.runCurrent()
        feature.reject()
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.failed(DAPP) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
    }

    @Test
    fun aDismissedPasscodeIsAnsweredAsRejected() {
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } throws
            CancellationException("passcode dismissed")
        val (feature, shown) = open(context = DAPP)
        scheduler.runCurrent()

        feature.sendTransaction(context, shown.last()!!)
        scheduler.runCurrent()
        feature.reject()
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.rejected(DAPP) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    private fun offer(vararg options: TxFee) {
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(
                energy = secondArg(),
                estimated = arg(6),
                options = options.toList(),
                quote = quote,
            )
        }
    }

    private fun open(context: ConfirmContext? = null): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID),
                type = ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(
                        chain = Chain.Ethereum.Mainnet,
                        to = TestWallet.ETH_ADDRESS,
                        amount = BigInteger.fromInt(1_000_000),
                    ),
                ),
                context = context,
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            passcodeManager = passcodeManager,
            confirmResults = confirmResults,
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
        val DAPP = ConfirmContext.Dapp(
            requestId = "confirm-1",
            topic = "earn.example",
            name = "Earn",
            url = "earn.example",
            source = ConfirmContext.Dapp.Source.Injected,
        )
        val NATIVE_FEE: Fee = Fee.Value(TestAssets.eth.value.chain.toAsset(), BigInteger.parseString("21672229775170"))
    }
}

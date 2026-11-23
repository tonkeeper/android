package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.chain.ethereum.utils.Hermes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The fee methods of an Ethereum transfer: what the relayer's estimate turns into, in which order, and
 * what emulating the selected one changes.
 */
class TxFeeBuilderEvmTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val batteryRepository = mockk<BatteryRepository>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val accountRepo = mockk<McAccountRepository>()
    private val settings = mockk<SettingsRepository>()
    private val gaslessRelayer = mockk<GaslessRelayer>()
    private val wallet = mockk<WalletEntity>()

    private val builder = TxFeeBuilder(
        api = api,
        batteryRepository = batteryRepository,
        unifiedAccountRepository = unifiedAccountRepository,
        accountRepo = accountRepo,
        settingsRepository = settings,
        tokenRepository = mockk(),
        sender = mockk(relaxed = true),
        gaslessRelayer = gaslessRelayer,
    )

    private val usdcAsset = testAsset(USDC_ID, "USDC", decimals = 6)
    private val eth by lazy { testAccount(TestAssets.eth, balance = "795000000000000") }
    private val usdt by lazy { testAccount(TestAssets.usdtEth, balance = "2006121") }
    private val usdc by lazy { testAccount(usdcAsset, balance = "5000000") }
    private val bnb by lazy { testAccount(testAsset("bsc/mainnet/coin", "BNB", decimals = 18), balance = "0") }
    private val doge by lazy { testAccount(testAsset(DOGE_WALLET_ID, "DOGE", decimals = 8), balance = "500000000") }

    private val nativeFee by lazy { Fee.Value(eth.asset.value, BigInteger.parseString("21672229775170")) }
    private var preferred = PreferredFeeMethod.UNSPECIFIED
    private var gaslessChains = setOf("eth/mainnet")
    private var estimations = listOf(
        GaslessRelayer.Estimation(Asset.Battery.ID, BigInteger.fromInt(18), available = true),
        GaslessRelayer.Estimation(USDC_ID, BigInteger.fromInt(115_304), available = false),
        GaslessRelayer.Estimation(USDT_ID, BigInteger.fromInt(115_304), available = true),
    )

    @Before
    fun setUp() {
        every { wallet.id } returns TestWallet.ID
        every { wallet.network } returns TonNetwork.MAINNET
        every { wallet.isMultichainLedger } returns false
        every { api.getConfig(any()) } returns mockk<ConfigEntity>(relaxed = true)
        coEvery { unifiedAccountRepository.getTonWalletById(TestWallet.ID) } returns wallet
        coEvery { unifiedAccountRepository.getAuthBy(TestWallet.ID) } returns Authorization(
            deviceToken = "device",
            walletId = TestWallet.ID,
        )
        coEvery { batteryRepository.getCharges(any(), any()) } returns 100
        coEvery { batteryRepository.getConfig(any(), any()) } returns mockk<BatteryConfigEntity> {
            every { chargeCost } returns "0.01"
        }
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } answers {
            when (secondArg<String>()) {
                USDC_ID -> usdc
                else -> null
            }
        }
        every { settings.getPreferredFeeMethod(any()) } answers { preferred }
        every { wallet.accountId } returns TestWallet.TON_ADDRESS
        every { gaslessRelayer.estimate(any(), any()) } answers { estimations }
        coEvery { batteryRepository.getGaslessChains(any(), any()) } answers { gaslessChains }
    }

    @Test
    fun aTokenTransferIsOfferedBatteryThenTheCoinThenTheTokenItself() = runTest {
        val state = build(amount = 1_000_000)

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), state.options.map { it.id })
        assertEquals(
            listOf("18", "21672229775170", "115304", "115304"),
            state.options.map { it.fee.amount.toString() },
        )
        assertNull(state.relayer)
        assertNotNull(state.quote)
    }

    @Test
    fun whatTheRelayerRulesOutIsListedLastAndCannotPay() = runTest {
        val state = build(amount = 1_000_000)

        assertEquals(listOf(true, true, true, false), state.options.map { TxFeeLogic.isSufficient(it) })
        assertEquals(USDC_ID, state.options.last().id)
    }

    @Test
    fun aBatteryWithoutTheChargesIsListedLast() = runTest {
        coEvery { batteryRepository.getCharges(any(), any()) } returns 3
        estimations = estimations.map { estimation ->
            GaslessRelayer.Estimation(estimation.assetId, estimation.fee, available = estimation.assetId == USDT_ID)
        }

        val state = build(amount = 1_000_000)

        assertEquals(listOf(eth.asset.id, usdt.asset.id, Asset.Battery.ID, USDC_ID), state.options.map { it.id })
        assertEquals(listOf(true, true, false, false), state.options.map { TxFeeLogic.isSufficient(it) })
    }

    @Test
    fun thePreferredKindLeads() = runTest {
        preferred = PreferredFeeMethod.GASLESS

        val state = build(amount = 1_000_000)

        assertEquals(listOf(usdt.asset.id, Asset.Battery.ID, eth.asset.id, USDC_ID), state.options.map { it.id })
    }

    @Test
    fun aPreferredFeeInATokenStartsWithTheOneBeingSent() = runTest {
        preferred = PreferredFeeMethod.GASLESS
        estimations = estimations.map { GaslessRelayer.Estimation(it.assetId, it.fee, available = true) }

        val state = build(amount = 1_000_000)

        // Only the sent token stands for the kind: another token stays below battery and the coin.
        assertEquals(listOf(usdt.asset.id, Asset.Battery.ID, eth.asset.id, USDC_ID), state.options.map { it.id })
    }

    @Test
    fun aFeeInTheSentTokenHasToFitNextToTheAmount() = runTest {
        val state = build(amount = 1_950_000)

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), state.options.map { it.id })
        assertEquals(listOf(true, true, false, false), state.options.map { TxFeeLogic.isSufficient(it) })
    }

    @Test
    fun anotherTokenOfTheWalletCanPay() = runTest {
        estimations = estimations.map { GaslessRelayer.Estimation(it.assetId, it.fee, available = true) }

        val state = build(amount = 1_000_000)

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), state.options.map { it.id })
        assertTrue(state.options.all { TxFeeLogic.isSufficient(it) })
    }

    @Test
    fun aCoinThatCannotCoverItsOwnFeeGoesBelowTheRelayedMethods() = runTest {
        val state = build(amount = 1_000_000, energy = testAccount(TestAssets.eth, balance = "1000"))

        assertEquals(listOf(Asset.Battery.ID, usdt.asset.id, eth.asset.id, USDC_ID), state.options.map { it.id })
        assertEquals(listOf(true, true, false, false), state.options.map { TxFeeLogic.isSufficient(it) })
    }


    /** On BSC the relayer labels a token erc20 while the wallet holds it as bep20: it still pays. */
    @Test
    fun aBscTokenTheRelayerLabelsErc20PaysFromTheWalletsBep20Account() = runTest {
        gaslessChains = setOf("bsc/mainnet")
        estimations = listOf(
            GaslessRelayer.Estimation(Asset.Battery.ID, BigInteger.fromInt(2), available = true),
            GaslessRelayer.Estimation(DOGE_RELAYER_ID, BigInteger.fromInt(1_000_000), available = true),
        )

        val state = buildBsc(amount = 100_000_000)

        assertEquals(listOf(Asset.Battery.ID, doge.asset.id, bnb.asset.id), state.options.map { it.id })
        assertTrue(TxFeeLogic.isSufficient(state.findOption(doge.asset.id)!!))
    }

    /** The relayer is asked about the token under its own spelling of the id. */
    @Test
    fun aBscTokenIsEmulatedUnderTheRelayersAssetId() = runTest {
        gaslessChains = setOf("bsc/mainnet")
        estimations = listOf(GaslessRelayer.Estimation(DOGE_RELAYER_ID, BigInteger.fromInt(1_000_000), available = true))
        every { gaslessRelayer.emulate(any(), any(), DOGE_RELAYER_ID) } returns emulation(DOGE_RELAYER_ID, fee = 1_100_000)

        val quoted = TxFeeLogic.quote(buildBsc(amount = 100_000_000), pickedId = doge.asset.id)

        assertEquals(doge.asset.id, quoted.quotedId)
        assertEquals("1100000", quoted.findOption(doge.asset.id)?.fee?.amount.toString())
    }

    @Test
    fun aMaxCoinTransferIsCarriedWholeWhileTheCoinCanStillPayItsGas() = runTest {
        val calls = mutableListOf<GaslessRelayer.Call>()
        every { gaslessRelayer.estimate(any(), capture(calls)) } answers { estimations }

        val state = buildWholeEthBalance(shrinkToFit = true)

        assertEquals(listOf(eth.unitBalance.value), calls.map { it.value })
        assertTrue(TxFeeLogic.isSufficient(state.findOption(eth.asset.id)!!))
    }

    @Test
    fun aCoinTransferOfTheWholeBalanceLeavesNothingForItsGas() = runTest {
        val state = buildWholeEthBalance(shrinkToFit = false)

        assertFalse(TxFeeLogic.isSufficient(state.findOption(eth.asset.id)!!))
    }

    @Test
    fun anEstimateThatFailsKeepsThePlainFee() = runTest {
        every { gaslessRelayer.estimate(any(), any()) } throws IllegalStateException("502")

        val state = build(amount = 1_000_000)

        assertTrue(state.options.isEmpty())
        assertEquals(nativeFee, state.estimated)
    }

    @Test
    fun theSelectedMethodIsPricedByItsEmulation() = runTest {
        every { gaslessRelayer.emulate(any(), any(), Asset.Battery.ID) } returns emulation(Asset.Battery.ID, fee = 19)

        val quoted = TxFeeLogic.quote(build(amount = 1_000_000), pickedId = null)

        assertEquals(Asset.Battery.ID, quoted.quotedId)
        assertEquals("19", quoted.findOption(Asset.Battery.ID)?.fee?.amount.toString())
        assertEquals("115304", quoted.findOption(usdt.asset.id)?.fee?.amount.toString())
        assertNotNull(quoted.relayer)
        verify(exactly = 1) { gaslessRelayer.emulate(any(), any(), any()) }
    }

    @Test
    fun aPickedTokenIsEmulatedUnderTheRelayersAssetId() = runTest {
        every { gaslessRelayer.emulate(any(), any(), USDT_ID) } returns emulation(USDT_ID, fee = 123_433)

        val quoted = TxFeeLogic.quote(build(amount = 1_000_000), pickedId = usdt.asset.id)

        assertEquals(usdt.asset.id, quoted.quotedId)
        assertEquals("123433", quoted.findOption(usdt.asset.id)?.fee?.amount.toString())
    }

    @Test
    fun anEmulationThatFailsRulesTheMethodOut() = runTest {
        every { gaslessRelayer.emulate(any(), any(), Asset.Battery.ID) } throws IllegalStateException("emulation_failed")

        val quoted = TxFeeLogic.quote(build(amount = 1_000_000), pickedId = null)

        assertEquals(listOf(eth.asset.id, usdt.asset.id, Asset.Battery.ID, USDC_ID), quoted.options.map { it.id })
        assertEquals(eth.asset.id, quoted.resolve(null)?.id)
        assertNull(quoted.quotedId)
    }

    @Test
    fun anEmulatedPriceTheBalanceCannotCoverHandsTheSelectionOn() = runTest {
        every { gaslessRelayer.emulate(any(), any(), USDT_ID) } returns emulation(USDT_ID, fee = 1_100_000)
        every { gaslessRelayer.emulate(any(), any(), Asset.Battery.ID) } returns emulation(Asset.Battery.ID, fee = 19)

        val quoted = TxFeeLogic.quote(build(amount = 1_000_000), pickedId = usdt.asset.id)

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), quoted.options.map { it.id })
        assertEquals(Asset.Battery.ID, quoted.quotedId)
        assertEquals(Asset.Battery.ID, quoted.resolve(usdt.asset.id)?.id)
    }

    @Test
    fun aSwapCallIsOfferedTheSameMethods() = runTest {
        val state = buildSwap(swap(amount = 0, data = SWAP_CALLDATA))

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), state.options.map { it.id })
        assertNotNull(state.quote)
        verify(exactly = 1) {
            gaslessRelayer.estimate(any(), match { it.type == GaslessRelayer.Call.Type.Swap && it.data == SWAP_CALLDATA })
        }
    }

    /** A swap call's tokens are inside its calldata: only what it is said to sell holds a fee in them to the balance. */
    @Test
    fun aFeeInTheTokenSoldHasToFitNextToWhatTheCallSells() = runTest {
        val fits = buildSwap(swap(amount = 0, data = SWAP_CALLDATA), sold = BigInteger.fromInt(2006121 - 115_304))
        val short = buildSwap(swap(amount = 0, data = SWAP_CALLDATA), sold = BigInteger.fromInt(2006121 - 115_303))

        assertTrue(checkNotNull(fits.findOption(usdt.asset.id)).available)
        assertFalse(checkNotNull(short.findOption(usdt.asset.id)).available)
        assertTrue("a fee in another asset is not held to what is sold", checkNotNull(short.findOption(eth.asset.id)).available)
    }

    @Test
    fun aSwapBehindAnApproveKeepsThePlainFee() = runTest {
        val approve = Transaction.Call(
            account = usdt.value,
            contract = Address.force(USDT_CONTRACT, Chain.Ethereum.Mainnet),
            amount = BigInteger.ZERO,
            data = "0x095ea7b3",
        )

        val state = buildSwap(swap(amount = 0, data = SWAP_CALLDATA), approvals = listOf(approve))

        assertTrue(state.options.isEmpty())
        assertNull(state.quote)
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    /** Where the operation is sent whole, the relayer carries the approve with the swap: one batch, priced as one call. */
    @Test
    fun aSwapWithItsApproveIsRelayedAsOneBatch() = runTest {
        val estimated = slot<GaslessRelayer.Call>()
        val emulated = slot<List<GaslessRelayer.Call>>()
        every { gaslessRelayer.estimate(any(), capture(estimated)) } answers { estimations }
        every { gaslessRelayer.emulate(any(), capture(emulated), Asset.Battery.ID) } returns emulation(Asset.Battery.ID, fee = 19)
        val approve = Transaction.Call(
            account = usdt.value,
            contract = Address.force(USDT_CONTRACT, Chain.Ethereum.Mainnet),
            amount = BigInteger.ZERO,
            data = APPROVE_ROUTER,
        )

        val state = buildSwap(swap(amount = 0, data = SWAP_CALLDATA), approvals = listOf(approve), relayApprovals = true)
        val quoted = TxFeeLogic.quote(state, pickedId = null)

        assertEquals(listOf(Asset.Battery.ID, eth.asset.id, usdt.asset.id, USDC_ID), state.options.map { it.id })
        // Priced as the call of the wallet to itself that runs both.
        assertTrue(estimated.captured.to.equals(TestWallet.ETH_ADDRESS, ignoreCase = true))
        assertEquals(BigInteger.ZERO, estimated.captured.value)
        assertEquals(executeOf(approveThenRouterCall()), estimated.captured.data)
        // Emulated, and so sent, as the approve and then the swap.
        assertEquals(Asset.Battery.ID, quoted.quotedId)
        assertEquals(listOf(USDT_CONTRACT.lowercase(), ROUTER.lowercase()), emulated.captured.map { it.to.lowercase() })
        assertEquals(listOf(APPROVE_ROUTER, SWAP_CALLDATA), emulated.captured.map { it.data })
    }

    @Test
    fun aChainTheRelayerDoesNotCarryKeepsThePlainFee() = runTest {
        gaslessChains = setOf("arb/mainnet")

        val state = build(amount = 1_000_000)

        assertTrue(state.options.isEmpty())
        assertNull(state.quote)
        assertEquals(nativeFee, state.estimated)
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    @Test
    fun aSwapOnAChainTheRelayerDoesNotCarryKeepsThePlainFee() = runTest {
        gaslessChains = setOf("arb/mainnet")

        val state = buildSwap(swap(amount = 0, data = SWAP_CALLDATA))

        assertTrue(state.options.isEmpty())
        assertNull(state.quote)
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    @Test
    fun noRelayerChainsKeepThePlainFee() = runTest {
        gaslessChains = emptySet()

        val state = build(amount = 1_000_000)

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    @Test
    fun aLedgerWalletKeepsThePlainFee() = runTest {
        every { wallet.isMultichainLedger } returns true

        val state = build(amount = 1_000_000)

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    /** The relayer priced the battery and the charges fall short: recharging pays the fee. */
    @Test
    fun aBatteryShortOnChargesIsRechargeable() = runTest {
        coEvery { batteryRepository.getCharges(any(), any()) } returns 3

        val state = build(amount = 1_000_000)

        assertTrue(state.batteryRechargeable)
    }

    @Test
    fun aChargedBatteryIsNotRechargeable() = runTest {
        val state = build(amount = 1_000_000)

        assertFalse(state.batteryRechargeable)
    }

    /** The relayer ruled the battery out: more charges would not make it pay. */
    @Test
    fun aBatteryTheRelayerRulesOutIsNotRechargeable() = runTest {
        coEvery { batteryRepository.getCharges(any(), any()) } returns 3
        estimations = estimations.map { GaslessRelayer.Estimation(it.assetId, it.fee, available = it.assetId != Asset.Battery.ID) }

        val state = build(amount = 1_000_000)

        assertFalse(state.batteryRechargeable)
    }

    /** Whatever the charges, a battery the relayer did not price cannot be offered for a recharge. */
    @Test
    fun anEstimateThatFailsIsNotRechargeable() = runTest {
        coEvery { batteryRepository.getCharges(any(), any()) } returns 0
        every { gaslessRelayer.estimate(any(), any()) } throws IllegalStateException("502")

        val state = build(amount = 1_000_000)

        assertFalse(state.batteryRechargeable)
    }

    @Test
    fun aChainTheRelayerDoesNotCarryIsNotRechargeable() = runTest {
        coEvery { batteryRepository.getCharges(any(), any()) } returns 0
        gaslessChains = emptySet()

        val state = build(amount = 1_000_000)

        assertFalse(state.batteryRechargeable)
    }

    private suspend fun build(
        amount: Int,
        energy: AccountWithDetails = eth,
        account: AccountWithDetails = usdt,
    ): TxFeeState = builder.build(
        account = account,
        energy = energy,
        walletId = TestWallet.ID,
        amount = BigInteger.fromInt(amount),
        to = TestWallet.ETH_ADDRESS,
        comment = null,
        estimated = nativeFee,
        shrinkToFit = false,
    )

    private suspend fun buildWholeEthBalance(shrinkToFit: Boolean): TxFeeState = builder.build(
        account = eth,
        energy = eth,
        walletId = TestWallet.ID,
        amount = eth.unitBalance.value,
        to = TestWallet.ETH_ADDRESS,
        comment = null,
        estimated = nativeFee,
        shrinkToFit = shrinkToFit,
    )

    private suspend fun buildBsc(amount: Int): TxFeeState = builder.build(
        account = doge,
        energy = bnb,
        walletId = TestWallet.ID,
        amount = BigInteger.fromInt(amount),
        to = TestWallet.ETH_ADDRESS,
        comment = null,
        estimated = Fee.Value(bnb.asset.value, BigInteger.parseString("30000000000000")),
        shrinkToFit = false,
    )

    private suspend fun buildSwap(
        transaction: Transaction.Swap,
        approvals: List<Transaction> = emptyList(),
        sold: BigInteger? = null,
        relayApprovals: Boolean = false,
    ): TxFeeState = builder.buildSwap(
        account = usdt,
        energy = eth,
        walletId = TestWallet.ID,
        transactions = listOf(transaction),
        approvals = approvals,
        estimated = nativeFee,
        attachedEnergy = BigInteger.ZERO,
        shrinkToFit = false,
        sold = sold,
        relayApprovals = relayApprovals,
    )

    @Test
    fun aSelfCallBatchIsEstimatedWholeAndEmulatedAsItsCalls() = runTest {
        val calls = slot<List<GaslessRelayer.Call>>()
        every { gaslessRelayer.emulate(any(), capture(calls), Asset.Battery.ID) } returns emulation(Asset.Battery.ID, fee = 19)

        val state = buildOperation(selfCall(executeOf(approveThenRouterCall())))
        val quoted = TxFeeLogic.quote(state, pickedId = null)

        assertEquals(Asset.Battery.ID, quoted.quotedId)
        assertEquals(listOf(USDT_CONTRACT.lowercase(), ROUTER.lowercase()), calls.captured.map { it.to.lowercase() })
        assertEquals(listOf(APPROVE_ROUTER, SWAP_CALLDATA), calls.captured.map { it.data })
        verify(exactly = 1) { gaslessRelayer.estimate(any(), match { it.to.equals(TestWallet.ETH_ADDRESS, ignoreCase = true) }) }
    }

    @Test
    fun aSelfCallThatIsNotAPlainBatchKeepsTheCoin() = runTest {
        val state = buildOperation(selfCall(executeOf(approveThenRouterCall()) + "00"))

        assertTrue(state.options.isEmpty())
        assertEquals(nativeFee, state.estimated)
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    @Test
    fun aCallToAnotherAddressIsEmulatedAsItIs() = runTest {
        val calls = slot<List<GaslessRelayer.Call>>()
        every { gaslessRelayer.emulate(any(), capture(calls), Asset.Battery.ID) } returns emulation(Asset.Battery.ID, fee = 19)

        TxFeeLogic.quote(buildOperation(call(to = ROUTER, data = SWAP_CALLDATA)), pickedId = null)

        assertEquals(listOf(ROUTER.lowercase()), calls.captured.map { it.to.lowercase() })
        assertEquals(listOf(SWAP_CALLDATA), calls.captured.map { it.data })
    }

    private suspend fun buildOperation(payload: Payload): TxFeeState {
        return builder.buildOperation(
            account = usdt,
            energy = eth,
            walletId = TestWallet.ID,
            payload = payload,
            spentAmount = BigInteger.fromInt(1_000_000),
            estimated = nativeFee,
        )
    }

    private fun selfCall(data: String) = call(to = TestWallet.ETH_ADDRESS, data = data)

    private fun call(to: String, data: String) = ConfirmOperations.payload(
        index = 0,
        chain = Chain.Ethereum.Mainnet,
        meta = PayloadMeta.Evm.Main(to = Address.force(to, Chain.Ethereum.Mainnet), value = BigInteger.ZERO, data = data),
    )

    private fun approveThenRouterCall() = listOf(
        Hermes.Call(Address.force(USDT_CONTRACT, Chain.Ethereum.Mainnet), BigInteger.ZERO, APPROVE_ROUTER),
        Hermes.Call(Address.force(ROUTER, Chain.Ethereum.Mainnet), BigInteger.ZERO, SWAP_CALLDATA),
    )

    private fun executeOf(calls: List<Hermes.Call>): String {
        return "0x" + Hermes.execute(Hermes.mode(), Hermes.encodeCalls(calls)).joinToString("") { "%02x".format(it) }
    }

    private fun swap(amount: Int, data: String?) = Transaction.Swap(
        account = usdt.value,
        amount = BigInteger.fromInt(amount),
        destination = eth.value,
        to = Address.force(ROUTER, Chain.Ethereum.Mainnet),
        isMax = false,
        data = data,
    )

    private fun emulation(assetId: String, fee: Int) = GaslessRelayer.Emulation(
        assetId = assetId,
        fee = BigInteger.fromInt(fee),
        operation = Operation.build("bcef74e1-b940-4142-b7a7-76161850971e", emptyList()),
        expiresAt = Long.MAX_VALUE,
    )

    private companion object {
        const val USDC_ID = "eth/mainnet/erc20/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        const val USDT_ID = "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val USDT_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val DOGE_WALLET_ID = "bsc/mainnet/bep20/0xba2ae424d960c26247dd6c32edc70b295c744c43"
        const val DOGE_RELAYER_ID = "bsc/mainnet/erc20/0xba2ae424d960c26247dd6c32edc70b295c744c43"
        const val ROUTER = "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"
        const val SWAP_CALLDATA = "0x38ed1739"
        const val APPROVE_ROUTER = "0x095ea7b30000000000000000000000007a250d5630b4cf539739df2c5dacb4c659f2488d00000000000000000000000000000000000000000000000000000000000f4240"
    }
}
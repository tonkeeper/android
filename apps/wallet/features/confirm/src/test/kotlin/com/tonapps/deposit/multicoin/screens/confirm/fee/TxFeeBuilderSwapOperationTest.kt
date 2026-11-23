package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.tron.TronTransactionRequest
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.tron.TronApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The fee methods of a `/v3` swap on the chains the gasless relayer carries: what the relayer is handed
 * of the operation's payloads, and what its estimate turns into.
 */
class TxFeeBuilderSwapOperationTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val tronApi = mockk<TronApi>()
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

    private val trx by lazy { testAccount(TestAssets.trx, balance = "0", address = SENDER) }
    private val usdt by lazy { testAccount(TestAssets.usdtTron, balance = "50000000", address = SENDER) }
    private val nativeFee by lazy { Fee.Value(trx.asset.value, BigInteger.fromInt(13_040_000)) }

    private val eth by lazy { testAccount(TestAssets.eth, balance = "795000000000000") }
    private val usdtEth by lazy { testAccount(TestAssets.usdtEth, balance = "2006121") }
    private val ton by lazy { testAccount(TestAssets.ton, balance = "0", address = TestWallet.TON_ADDRESS) }
    private val usdtTon by lazy { testAccount(TestAssets.usdtTon, balance = "50000000", address = TestWallet.TON_ADDRESS) }
    private val sol by lazy { testAccount(SOL, balance = "1000000000", address = SOL_SENDER) }
    private val usdtSol by lazy { testAccount(USDT_SOL, balance = "5000000", address = SOL_SENDER) }

    private val asked = slot<GaslessRelayer.Call>()
    private var gaslessChains = setOf("tron/mainnet")
    private var estimations = listOf(
        GaslessRelayer.Estimation(Asset.Battery.ID, BigInteger.fromInt(532), available = true),
        GaslessRelayer.Estimation("ton/mainnet/coin", BigInteger.parseString("1729000000"), available = true),
        GaslessRelayer.Estimation(TestAssets.usdtTron.id, BigInteger.fromInt(3_140_000), available = true),
    )

    @Before
    fun setUp() {
        every { wallet.id } returns TestWallet.ID
        every { wallet.network } returns TonNetwork.MAINNET
        every { wallet.isMultichainLedger } returns false
        every { wallet.accountId } returns TestWallet.TON_ADDRESS
        every { api.getConfig(any()) } returns mockk<ConfigEntity>(relaxed = true)
        every { api.tron } returns tronApi
        coEvery { unifiedAccountRepository.getTonWalletById(TestWallet.ID) } returns wallet
        coEvery { unifiedAccountRepository.getAuthBy(TestWallet.ID) } returns Authorization(
            deviceToken = "device",
            walletId = TestWallet.ID,
        )
        coEvery { batteryRepository.getCharges(any(), any()) } returns 1000
        coEvery { batteryRepository.getConfig(any(), any()) } returns mockk<BatteryConfigEntity> {
            every { chargeCost } returns "0.01"
        }
        coEvery { batteryRepository.getGaslessChains(any(), any()) } answers { gaslessChains }
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns null
        every { settings.getPreferredFeeMethod(any()) } returns PreferredFeeMethod.UNSPECIFIED
        every { gaslessRelayer.estimate(any(), capture(asked)) } answers { estimations }
    }

    /** A TRON `main` goes to the relayer as the swap states it: nothing is built of it on the device. */
    @Test
    fun aCallIsHandedOverAsTheSwapStatesIt() = runTest {
        build(call())

        assertEquals(call(), asked.captured.tron)
        assertEquals("tron/mainnet", asked.captured.chainId)
        assertEquals(SENDER, asked.captured.sender)
        assertNull(asked.captured.intent)
        verify(exactly = 0) { tronApi.buildSmartContractTransaction(any()) }
    }

    /** What the relayer builds is held to the call: its contract, calldata without `0x`, TRX and memo. */
    @Test
    fun theRelayersTransactionIsHeldToTheCallAskedFor() = runTest {
        build(call(data = "0x$TRANSFER", memo = MEMO))

        val request = asked.captured.tronRequest as TronTransactionRequest.Call
        assertEquals(SENDER, request.from)
        assertEquals(USDT_CONTRACT, request.contractAddress)
        assertEquals(TRANSFER, request.callData)
        assertEquals(0L, request.callValue)
        assertEquals(MEMO, request.memo)
    }

    /** A `main` with no calldata is TRX to its address, with its memo: it goes to the relayer as well. */
    @Test
    fun aDepositIsHandedOverAsTheSwapStatesIt() = runTest {
        val deposit = call(data = null, memo = MEMO).copy(to = Address.force(SENDER, Chain.Tron.Mainnet), value = BigInteger.fromInt(5_000_000))

        build(deposit, account = trx)

        assertEquals(deposit, asked.captured.tron)
        val request = asked.captured.tronRequest as TronTransactionRequest.Trx
        assertEquals(SENDER, request.to)
        assertEquals(5_000_000L, request.amountSun)
        assertEquals(MEMO, request.memo)
    }

    /** A `main` with no calldata moves TRX, as chainkit signs it: a sale of USDT is never one, and is not relayed. */
    @Test
    fun aTokenSaleWithNoCalldataIsNotRelayed() = runTest {
        val state = build(call(data = null).copy(to = Address.force(SENDER, Chain.Tron.Mainnet)))

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    /** A fee in a token would be a second transaction beside the one built: only the battery is listed with the coin. */
    @Test
    fun theBatteryAloneIsListedBesideTheCoin() = runTest {
        val state = build(call())

        assertEquals(listOf(Asset.Battery.ID, trx.asset.id), state.options.map { it.id })
        assertEquals(listOf("532", "13040000"), state.options.map { it.fee.amount.toString() })
        assertTrue(state.hasRelayedOption)
        assertNotNull(state.quote)
    }

    @Test
    fun aRoutesOwnTransactionIsHandedOverAsItIs() = runTest {
        build(PayloadMeta.Tron.Prebuild(ROUTE_BUILT))

        assertEquals(PayloadMeta.Tron.Prebuild(ROUTE_BUILT), asked.captured.tron)
    }

    @Test
    fun aChainTheRelayerDoesNotCarryBuildsNothing() = runTest {
        gaslessChains = setOf("eth/mainnet")

        val state = build(call())

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    @Test
    fun aLedgerWalletBuildsNothing() = runTest {
        every { wallet.isMultichainLedger } returns true

        val state = build(call())

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    /** What the relayer refuses, a TRON `main` today among it, is paid in TRX. */
    @Test
    fun aMainTheRelayerRefusesIsPaidInTheCoin() = runTest {
        every { gaslessRelayer.estimate(any(), any()) } throws IllegalStateException("unsupported_payload")

        val state = build(call())

        assertTrue(state.options.isEmpty())
        assertEquals(nativeFee, state.estimated)
    }

    // region EVM

    @Test
    fun anEvmCallIsHandedOverAsThePayloadStatesIt() = runTest {
        gaslessChains = setOf("eth/mainnet")

        buildEvm(evmCall(ROUTER, SWAP_CALLDATA, value = 7))

        assertEquals(ROUTER, asked.captured.to)
        assertEquals(BigInteger.fromInt(7), asked.captured.value)
        assertEquals(SWAP_CALLDATA, asked.captured.data)
        assertEquals("eth/mainnet", asked.captured.chainId)
        assertEquals(GaslessRelayer.Call.Type.Swap, asked.captured.type)
    }

    /** The relayer prices one call, and the swap alone reverts without its approve: the two are priced as the batch that runs both. */
    @Test
    fun anApproveAndItsSwapArePricedAsOneBatch() = runTest {
        gaslessChains = setOf("eth/mainnet")

        val state = buildEvm(evmCall(USDT_ETH_CONTRACT, APPROVE_ROUTER), evmCall(ROUTER, SWAP_CALLDATA))

        assertEquals(usdtEth.data.displayAddress, asked.captured.to)
        assertEquals(BigInteger.ZERO, asked.captured.value)
        assertNotNull(state.quote)
    }

    @Test
    fun aPayloadThatIsNotAnEvmCallLeavesTheRouteWithTheCoin() = runTest {
        gaslessChains = setOf("eth/mainnet")

        val state = buildEvm(tronPayload(call()), evmCall(ROUTER, SWAP_CALLDATA))

        assertTrue(state.options.isEmpty())
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    /** The amounts are signed as prepared: a fee in the token sold has to fit next to what is sold. */
    @Test
    fun aFeeInTheTokenSoldHasToFitNextToWhatIsSold() = runTest {
        gaslessChains = setOf("eth/mainnet")
        estimations = listOf(GaslessRelayer.Estimation(usdtEth.asset.id, BigInteger.fromInt(115_304), available = true))

        val state = buildEvm(evmCall(ROUTER, SWAP_CALLDATA), sold = 2_000_000)

        assertEquals(listOf(false), state.options.filter { it.id == usdtEth.asset.id }.map { TxFeeLogic.isSufficient(it) })
    }

    /** A max sale is prepared again to sell what the fee leaves: nothing is held against the fee. */
    @Test
    fun aMaxSaleIsNotHeldToWhatItSells() = runTest {
        gaslessChains = setOf("eth/mainnet")
        estimations = listOf(GaslessRelayer.Estimation(usdtEth.asset.id, BigInteger.fromInt(115_304), available = true))

        val state = buildEvm(evmCall(ROUTER, SWAP_CALLDATA), sold = 2_000_000, shrinkToFit = true)

        assertEquals(listOf(true), state.options.filter { it.id == usdtEth.asset.id }.map { TxFeeLogic.isSufficient(it) })
    }

    // endregion

    // region TON

    /** A jetton's swap message goes to the relayer as the operation states it, held to what the swap sells. */
    @Test
    fun aTonJettonSwapIsHandedOverAsTheMessageItIs() = runTest {
        gaslessChains = setOf("ton/mainnet")

        buildTon(usdtTon, tonMessage())

        assertEquals(tonMessage(), asked.captured.ton)
        assertEquals("ton/mainnet", asked.captured.chainId)
        assertEquals(Intent.Spend(usdtTon.asset.value, BigInteger.fromInt(1_000_000)), asked.captured.intent?.spends?.single())
    }

    /** The TON the message carries is the relayer's to attach: the wallet is not asked to hold it. */
    @Test
    fun theTonAMessageCarriesIsTheRelayersToAttach() = runTest {
        gaslessChains = setOf("ton/mainnet")

        val state = buildTon(usdtTon, tonMessage())

        assertEquals(BigInteger.fromInt(260_000_000), state.attachedEnergy)
        assertEquals(listOf(Asset.Battery.ID), state.options.filter { TxFeeLogic.isSufficient(it) }.map { it.id })
        assertTrue(state.hasRelayedOption)
    }

    /** The parts of a split TON route are priced one by one: the battery pays for them together, at their sum. */
    @Test
    fun aSplitTonRouteIsPricedPartByPart() = runTest {
        gaslessChains = setOf("ton/mainnet")
        estimations = listOf(GaslessRelayer.Estimation(Asset.Battery.ID, BigInteger.fromInt(102), available = true))
        val parts = listOf(tonMessage(), tonMessage().copy(value = BigInteger.fromInt(150_000_000)))

        val state = buildTon(usdtTon, parts)

        verify(exactly = 2) { gaslessRelayer.estimate(any(), any()) }
        assertEquals(BigInteger.fromInt(204), state.options.first { it.isBattery }.fee.amount)
        assertEquals(BigInteger.fromInt(410_000_000), state.attachedEnergy)
    }

    /** A route split into parts comes as a batch: each part is priced on its own and goes to the relayer as its own message. */
    /** A message that carries the whole balance by its send mode goes to the relayer as it is, mode and all. */
    @Test
    fun aTonMessageCarryingTheBalanceIsAskedAsItIs() = runTest {
        gaslessChains = setOf("ton/mainnet")

        buildTon(ton, tonMessage().copy(mode = 128))

        assertEquals(128, asked.captured.ton?.mode)
    }

    /** A batch with no message is no route: it fails rather than being paid as one. */
    @Test
    fun anEmptyBatchFails() = runTest {
        gaslessChains = setOf("ton/mainnet")

        val failure = runCatching {
            builder.buildSwapOperation(
                account = usdtTon,
                energy = ton,
                walletId = TestWallet.ID,
                payloads = listOf(ConfirmOperations.payload(0, Chain.Ton.Mainnet, PayloadMeta.Ton.Batch(calls = emptyList()))),
                sold = BigInteger.fromInt(1_000_000),
                shrinkToFit = false,
                estimated = Fee.Value(ton.asset.value, BigInteger.fromInt(5_000_000)),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun aBatchGoesToTheRelayerPartByPart() = runTest {
        gaslessChains = setOf("ton/mainnet")
        estimations = listOf(GaslessRelayer.Estimation(Asset.Battery.ID, BigInteger.fromInt(102), available = true))
        val parts = listOf(tonMessage(), tonMessage().copy(value = BigInteger.fromInt(150_000_000)))
        val batch = PayloadMeta.Ton.Batch(calls = parts)

        val state = builder.buildSwapOperation(
            account = usdtTon,
            energy = ton,
            walletId = TestWallet.ID,
            payloads = listOf(ConfirmOperations.payload(0, Chain.Ton.Mainnet, batch)),
            sold = BigInteger.fromInt(1_000_000),
            shrinkToFit = false,
            estimated = Fee.Value(ton.asset.value, BigInteger.fromInt(5_000_000)),
        )

        verify(exactly = 2) { gaslessRelayer.estimate(any(), any()) }
        assertEquals(parts.last(), asked.captured.ton)
        assertEquals(BigInteger.fromInt(204), state.options.first { it.isBattery }.fee.amount)
    }

    /** A sale of TON itself goes to the relayer as its message too; the TON it carries is the sale, the account's own. */
    @Test
    fun aSaleOfTheTonCoinIsAskedOfTheRelayerAsItStands() = runTest {
        gaslessChains = setOf("ton/mainnet")

        val state = buildTon(ton, tonMessage())

        assertEquals(tonMessage(), asked.captured.ton)
        assertEquals(BigInteger.ZERO, state.attachedEnergy)
    }

    /** A jetton deposit goes to the relayer with its jetton, as the swap states it. */
    @Test
    fun aJettonDepositIsOfferedNoRelayerYet() = runTest {
        gaslessChains = setOf("ton/mainnet")
        val deposit = tonMessage().copy(body = null, token = Address.force(USDT_TON_MASTER, Chain.Ton.Mainnet), comment = "deposit-42")

        val state = buildTon(usdtTon, deposit)

        assertTrue(state.options.none { it.viaRelayer })
        assertEquals("the jetton's amount is not TON the swap carries", BigInteger.ZERO, state.attachedEnergy)
        verify(exactly = 0) { gaslessRelayer.estimate(any(), any()) }
    }

    // endregion

    // region Solana

    @Test
    fun aSolanaDepositIsHandedOverWithItsMemoAndHeldToItsRecipient() = runTest {
        gaslessChains = setOf("sol/mainnet")
        val deposit = PayloadMeta.Solana.Main(
            to = Address.force(SOL_RECIPIENT, Chain.Solana.Mainnet),
            value = BigInteger.fromInt(1_000_000),
            memo = "deposit-42",
            token = usdtSol.asset.value.let { (it as Asset.Token).contract },
        )

        buildSolana(deposit)

        assertEquals(deposit, asked.captured.solana)
        val intent = checkNotNull(asked.captured.intent)
        assertEquals(Intent.Spend(usdtSol.asset.value, BigInteger.fromInt(1_000_000)), intent.spends?.single())
        assertEquals(SOL_RECIPIENT, intent.to?.display)
    }

    /** A provider's transaction names no amount and no recipient: it is held to what the swap sells. */
    @Test
    fun aSolanaProviderTransactionIsHeldToWhatTheSwapSells() = runTest {
        gaslessChains = setOf("sol/mainnet")
        val provided = PayloadMeta.Solana.Prebuild("AQAAAA==")

        buildSolana(provided, sold = 3_000_000)

        assertEquals(provided, asked.captured.solana)
        val intent = checkNotNull(asked.captured.intent)
        assertEquals(Intent.Spend(usdtSol.asset.value, BigInteger.fromInt(3_000_000)), intent.spends?.single())
        assertNull(intent.to)
    }

    @Test
    fun aMaxSaleOfASolanaTokenIsStillHeldToWhatItSells() = runTest {
        gaslessChains = setOf("sol/mainnet")

        buildSolana(PayloadMeta.Solana.Prebuild("AQAAAA=="), sold = 3_000_000, shrinkToFit = true)

        assertEquals(BigInteger.fromInt(3_000_000), asked.captured.intent?.spends?.single()?.amount)
    }

    // endregion

    private suspend fun build(meta: PayloadMeta.Tron, account: AccountWithDetails = usdt) = builder.buildSwapOperation(
        account = account,
        energy = trx,
        walletId = TestWallet.ID,
        payloads = listOf(tronPayload(meta)),
        sold = BigInteger.fromInt(20_000_000),
        shrinkToFit = false,
        estimated = nativeFee,
    )

    private suspend fun buildEvm(vararg payloads: Payload, sold: Int = 1_000_000, shrinkToFit: Boolean = false) = builder.buildSwapOperation(
        account = usdtEth,
        energy = eth,
        walletId = TestWallet.ID,
        payloads = payloads.toList(),
        sold = BigInteger.fromInt(sold),
        shrinkToFit = shrinkToFit,
        estimated = Fee.Value(eth.asset.value, BigInteger.parseString("21672229775170")),
    )

    private suspend fun buildSolana(meta: PayloadMeta.Solana, sold: Int = 1_000_000, shrinkToFit: Boolean = false) = builder.buildSwapOperation(
        account = usdtSol,
        energy = sol,
        walletId = TestWallet.ID,
        payloads = listOf(ConfirmOperations.payload(0, Chain.Solana.Mainnet, meta)),
        sold = BigInteger.fromInt(sold),
        shrinkToFit = shrinkToFit,
        estimated = Fee.Value(sol.asset.value, BigInteger.fromInt(5_000)),
    )

    private suspend fun buildTon(account: AccountWithDetails, meta: PayloadMeta.Ton.Main) = buildTon(account, listOf(meta))

    private suspend fun buildTon(account: AccountWithDetails, messages: List<PayloadMeta.Ton.Main>) =
        builder.buildSwapOperation(
            account = account,
            energy = ton,
            walletId = TestWallet.ID,
            payloads = messages.mapIndexed { index, meta -> ConfirmOperations.payload(index, Chain.Ton.Mainnet, meta) },
            sold = BigInteger.fromInt(1_000_000),
            shrinkToFit = false,
            estimated = Fee.Value(ton.asset.value, BigInteger.fromInt(5_000_000)),
        )

    private fun tonMessage() = PayloadMeta.Ton.Main(
        to = Address.force(TON_ROUTER, Chain.Ton.Mainnet),
        value = BigInteger.fromInt(260_000_000),
        body = "te6cckEBAQEAAgAAAEysuc0=",
    )

    private fun tronPayload(meta: PayloadMeta.Tron) = ConfirmOperations.payload(0, Chain.Tron.Mainnet, meta)

    private fun evmCall(to: String, data: String, value: Int = 0) = ConfirmOperations.payload(
        index = 0,
        chain = Chain.Ethereum.Mainnet,
        meta = PayloadMeta.Evm.Main(
            to = Address.force(to, Chain.Ethereum.Mainnet),
            value = BigInteger.fromInt(value),
            data = data,
        ),
    )

    private fun call(data: String? = TRANSFER, memo: String? = null) = PayloadMeta.Tron.Main(
        to = Address.force(USDT_CONTRACT, Chain.Tron.Mainnet),
        value = BigInteger.ZERO,
        data = data,
        memo = memo,
    )

    // The transaction the node built, its expiration extended: the document is all the relayer is given.
    private companion object {
        const val SENDER = "TT2T17KZhoDu47i2E4FWxfG79zdkEWkU9N"
        const val USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TRANSFER = "a9059cbb" +
            "0000000000000000000000000ecb1ebffb8ba97e2985cbda3cbd7ec88352718e" +
            "0000000000000000000000000000000000000000000000000000000001312d00"
        const val MEMO = "71756f74652d3031"
        const val TON_ROUTER = "EQCI-Ihf6dqiu6qlMpduSeufqZdSGJsSLApaxPuTIs9qwtpW"
        const val USDT_TON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
        const val ROUTER = "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"
        const val USDT_ETH_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val SWAP_CALLDATA = "0x38ed1739"
        const val APPROVE_ROUTER = "0x095ea7b30000000000000000000000007a250d5630b4cf539739df2c5dacb4c659f2488d" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
        const val SOL_SENDER = "1111111111111111111111111111111" + "2"
        const val SOL_RECIPIENT = "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1"
        val SOL = testAsset("sol/mainnet/coin", "SOL", "Solana", decimals = 9)
        val USDT_SOL = testAsset("sol/mainnet/spl/Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", "USDT", "Tether USD", decimals = 6)
        const val ROUTE_BUILT = """{"raw_data_hex":"0a02f5ed","txID":"c730","visible":true}"""
    }
}

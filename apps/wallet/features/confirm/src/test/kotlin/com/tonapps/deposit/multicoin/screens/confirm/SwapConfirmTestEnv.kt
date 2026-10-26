package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.signing.SigSet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.ExchangeAPI
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import com.tonapps.wallet.testing.testWallet
import io.exchangeapi.apis.CrosschainApi
import io.exchangeapi.infrastructure.Serializer
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapConfig
import io.exchangeapi.models.CrossSwapPrepare
import io.exchangeapi.models.CrossSwapQuote
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The world a [SwapConfirmFeature] prepares a swap in.
 *
 * Real: the feature, the real [SwapRepository] (quote mapping, and the chainkit payload parsing
 * behind `prepareRoute`, which is what tells `exact` calldata from `flex`), and the route-to-operation
 * binding that builds the transactions.
 *
 * Faked: the HTTP layer, answering with quote/prepare responses recorded from
 * `https://swap.tonkeeper.com` (`src/test/resources`), and [ChainKitProvider], so a test can say what
 * the chain reports for fee, nonce and — the interesting one — the gas reservation that decides
 * whether a MAX amount gets shrunk.
 */
class SwapConfirmTestEnv(private val main: CoroutineDispatcher) {

    val quoteRequests = CopyOnWriteArrayList<CreateCrossSwapQuoteRequest>()
    val prepareRequests = CopyOnWriteArrayList<String>()

    /** The X-Wallet-ID each quote and prepare request went out with; null for an unregistered wallet. */
    val requestWalletIds = CopyOnWriteArrayList<String?>()

    /** The legacy swap wallet the request's wallet resolves to; null is a multichain wallet. */
    var legacyWallet: LegacySwapWallet? = null

    /** Accounts the legacy wallet reads, keyed by asset id. */
    val legacyAccountsByAsset = linkedMapOf<String, AccountWithDetails>()

    /** Whether the passcode a legacy send asks for is entered; false is a dismissed prompt. */
    var passcodeConfirmed = true

    /** Legacy wallets whose raw keys were read, by id. */
    val legacyKeyReads = CopyOnWriteArrayList<String>()

    /** Legacy wallets told a transaction went out, by id. */
    val legacySendsNotified = CopyOnWriteArrayList<String>()

    /** The wallet id each broadcast reported, null when it reported none. */
    val sentWalletIds = CopyOnWriteArrayList<String?>()
    val feeRequests = CopyOnWriteArrayList<Transaction>()
    val reserveCalls = CopyOnWriteArrayList<ReserveCall>()

    /** What the chain says is left after gas; the default reserves nothing. */
    var reserveAnswer: (ReserveCall) -> NodeRes<GasReserveResult> = { call ->
        NodeRes.ok(GasReserveResult(amount = call.amount, isAmountAdjusted = false, error = null))
    }

    var quoteAnswer: (CreateCrossSwapQuoteRequest) -> CrossSwapQuote = { SwapConfirmFixtures.quote(EXACT_TON) }
    var prepareAnswer: (String) -> CrossSwapPrepare = { SwapConfirmFixtures.prepare(EXACT_TON) }
    var feeAnswer: (Transaction) -> ChainRes<Fee> = { ChainRes.ok(Fee.None(it.account.chain.toAsset())) }
    var nonceAnswer: () -> NodeRes<BigInteger> = { NodeRes.ok(BigInteger.ONE) }

    /** Fee methods offered besides the chain's own estimate, a charged battery for one. */
    var feeOptions: List<TxFee> = emptyList()

    val accounts = linkedMapOf(
        SwapConfirmFixtures.TON_ASSET_ID to SwapConfirmFixtures.tonAccount,
        SwapConfirmFixtures.USDT_TON_ASSET_ID to SwapConfirmFixtures.usdtTonAccount,
        SwapConfirmFixtures.ETH_ASSET_ID to SwapConfirmFixtures.ethAccount,
        SwapConfirmFixtures.USDT_ETH_ASSET_ID to SwapConfirmFixtures.usdtEthAccount,
        SwapConfirmFixtures.BERRY_ASSET_ID to SwapConfirmFixtures.berryAccount,
        SwapConfirmFixtures.HMSTR_ASSET_ID to SwapConfirmFixtures.hmstrAccount,
    )

    private val crosschain = mockk<CrosschainApi>()
    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val settings = mockk<SettingsRepository>()
    private val legacyAccounts = mockk<LegacySwapAccounts>()
    private val passcodeManager = mockk<PasscodeManager>(relaxed = true)

    val swapRepo: SwapRepository

    init {
        val exchange = mockk<ExchangeAPI>()
        val api = mockk<API>()
        every { api.exchange } returns exchange
        every { exchange.swapV2 } returns crosschain
        every { crosschain.createCrossSwapQuote(any(), any(), any()) } answers {
            val request = firstArg<CreateCrossSwapQuoteRequest>()
            quoteRequests += request
            requestWalletIds += secondArg<String?>()
            quoteAnswer(request)
        }
        every { crosschain.prepareCrossSwapRoute(any(), any(), any(), any()) } answers {
            val routeId = firstArg<String>()
            prepareRequests += routeId
            requestWalletIds += secondArg<String?>()
            prepareAnswer(routeId)
        }
        every { crosschain.getCrossSwapConfig(any(), any(), any()) } answers { SwapConfirmFixtures.config }

        every { settings.installId } returns "test-install-id"

        swapRepo = SwapRepository(api, settings)

        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { legacyAccounts.find(any()) } answers { legacyWallet }
        coEvery { legacyAccounts.findAccount(any(), any(), any()) } answers { legacyAccountsByAsset[secondArg<String>()] }
        coEvery { legacyAccounts.signingKeys(any()) } answers {
            legacyKeyReads += firstArg<LegacySwapWallet>().id
            SigningWallet.Keys(emptyMap())
        }
        every { legacyAccounts.onTransactionSent(any()) } answers {
            legacySendsNotified += firstArg<LegacySwapWallet>().id
        }
        coEvery { passcodeManager.confirmation(any(), any()) } answers { passcodeConfirmed }
        coEvery { provider.signTransaction(any(), any(), any(), any()) } returns SignRes.ok(SigSet(byteArrayOf(1)))
        coEvery { provider.sendTransaction(any(), any(), any(), any(), any(), any(), any()) } answers {
            sentWalletIds += firstArg<String?>()
            NodeRes.ok("hash")
        }
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } answers {
            accounts[secondArg<String>()]
        }

        coEvery { provider.estimateFee(any<Transaction>()) } answers {
            val transaction = firstArg<Transaction>()
            feeRequests += transaction
            feeAnswer(transaction)
        }
        coEvery { provider.estimateBatchFee(any()) } answers {
            val batch = firstArg<List<Transaction.Swap>>()
            val largest = batch.maxBy { it.data?.length ?: 0 }
            feeRequests += largest
            val result = feeAnswer(largest)
            val fee = result.value
            if (batch.size == 1 || fee == null) {
                result
            } else {
                ChainRes.ok(Fee.Value(asset = fee.asset, amount = fee.amount * BigInteger.fromInt(batch.size)))
            }
        }
        coEvery { provider.getNonce(any()) } answers { nonceAnswer() }
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            val call = ReserveCall(
                account = firstArg(),
                energy = secondArg(),
                amount = thirdArg(),
                isMax = arg(3),
                fee = arg(4),
                policy = arg(5),
            )
            reserveCalls += call
            reserveAnswer(call)
        }

        coEvery { feeBuilder.buildSwap(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(energy = arg(1), estimated = arg(5), options = feeOptions)
        }
    }

    // ViewModelStore is the only public way to cancel a ViewModel: clear() itself is internal.
    private val store = ViewModelStore()
    private var featureCount = 0

    fun feature(request: ConfirmRequest): SwapConfirmFeature = SwapConfirmFeature(
        request = request,
        provider = provider,
        accountRepo = accountRepo,
        postTransactionRefreshSchedule = mockk(relaxed = true),
        swapRepo = swapRepo,
        passcodeManager = passcodeManager,
        confirmResults = mockk(relaxed = true),
        feeBuilder = feeBuilder,
        gaslessSender = mockk(relaxed = true),
        settingsRepository = mockk(relaxed = true),
        legacyAccounts = legacyAccounts,
    ).also { store.put("swap-confirm-${featureCount++}", it) }

    /**
     * `pendingTx` is cold and re-prepares per collector, so a test shares one collection the way the
     * screen does.
     */
    fun collectPending(feature: SwapConfirmFeature): List<PendingTransaction?> {
        val collected = CopyOnWriteArrayList<PendingTransaction?>()
        CoroutineScope(main).launch { feature.pendingTx.collect { collected += it } }
        return collected
    }

    /** Cancels the features: the quote countdown otherwise ticks forever in virtual time. */
    fun clear() {
        store.clear()
    }

    data class ReserveCall(
        val account: Account,
        val energy: Account,
        val amount: BigInteger,
        val isMax: Boolean,
        val fee: Fee?,
        val policy: GasReservePolicy,
    )

    companion object {
        // `exact` calldata, in the deposit shape (`to`/`value`).
        const val EXACT_TON = "exact_ton"

        // The same pair as the shipped app asks for it (`Tonkeeper/26.09.x`): a TON BOC whose
        // payload carries `address`/`amount` and no deposit address.
        const val TON_BOC = "ton_boc"

        const val FLEX_ETH = "flex_eth"
        const val TOKEN_ETH = "token_eth"

        // BERRY -> ETH through swapsxyz: an `exact` ERC-20 approve ahead of an `exact` router call.
        // Neither payload carries a gas price, so the provider quotes no fee for either.
        const val BERRY_APPROVAL = "berry_approval"

        // TON -> HMSTR through Omniston, split across pools: one `ton_boc` payload of four messages,
        // each carrying forward gas on top of its part of the amount. Inline on the quote (10 TON
        // in, 11.14 TON sent), and through prepare (100 TON in, 101.15 TON sent).
        const val OMNISTON_SPLIT = "omniston_ton_hmstr_split"
        const val OMNISTON_SPLIT_PREPARE = "omniston_ton_hmstr_split_prepare"
    }
}

/** Assets, accounts and recorded backend responses for the swap confirm tests. */
object SwapConfirmFixtures {

    const val WALLET_ID = TestWallet.ID

    val TON_ASSET_ID = TestAssets.ton.id
    val USDT_TON_ASSET_ID = TestAssets.usdtTon.id
    val ETH_ASSET_ID = TestAssets.eth.id
    val USDT_ETH_ASSET_ID = TestAssets.usdtEth.id

    // Recorded sell amounts, matching the routes in the fixtures.
    val TON_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("1000000000")
    val ETH_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("10000000000000000")
    val USDT_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("20000000")

    val tonAccount: AccountWithDetails = testAccount(TestAssets.ton, balance = "5000000000", price = "1.43")
    val usdtTonAccount: AccountWithDetails = testAccount(TestAssets.usdtTon, balance = "0", price = "1.0")
    val ethAccount: AccountWithDetails = testAccount(TestAssets.eth, balance = "100000000000000000", price = "2500")
    val usdtEthAccount: AccountWithDetails = testAccount(TestAssets.usdtEth, balance = "50000000", price = "1.0")

    val berryAsset = testAsset("eth/mainnet/erc20/0xcb76314c2540199f4b844d4ebbc7998c604880ca", "BERRY", decimals = 18)
    val BERRY_ASSET_ID = berryAsset.id
    val BERRY_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("15000000000000000000")
    val berryAccount: AccountWithDetails = testAccount(berryAsset, balance = "20000000000000000000", price = "0.01")

    val hmstrAsset = testAsset("ton/mainnet/jetton/0:09f2e59dec406ab26a5259a45d7ff23ef11f3e5c7c21de0b0d2a1cbe52b76b3d", "HMSTR", decimals = 9)
    val HMSTR_ASSET_ID = hmstrAsset.id
    val hmstrAccount: AccountWithDetails = testAccount(hmstrAsset, balance = "0", price = "0.0002")

    /** The swap screen's snapshot of the recorded BERRY route; it carries no payloads, so confirm prepares it. */
    val berrySnapshot = ConfirmType.Swap.Quote(
        routeId = "41b9a80c-1ec9-4735-ba79-17ca972dc290",
        buyAmount = BigInteger.parseString("68267013167247"),
        minBuyAmount = BigInteger.parseString("67584343035574"),
        provider = "swapsxyz",
        providerTxId = "0x2dd97880b0bcd832453e80c92b032ffc17f22f019d9c713c0b57aa531e83b627",
        slippageBps = 100,
    )

    val config: CrossSwapConfig by lazy { decodeResource("confirm_config.json") }

    fun quote(name: String): CrossSwapQuote = decodeResource("confirm_quote_$name.json")

    fun prepare(name: String): CrossSwapPrepare = decodeResource("confirm_prepare_$name.json")

    /** The confirm request the swap screen hands over, with or without its quote snapshot. */
    fun swapRequest(
        assetId: String = TON_ASSET_ID,
        destinationAssetId: String = USDT_TON_ASSET_ID,
        sourceAmount: BigInteger = TON_SOURCE_AMOUNT,
        isMax: Boolean = false,
        slippageBps: Int? = 100,
        quote: ConfirmType.Swap.Quote? = null,
    ) = ConfirmRequest(
        data = CommonTransactionData(assetId = assetId, walletId = WALLET_ID),
        type = ConfirmType.Swap(
            sourceAmount = sourceAmount,
            destinationAssetId = destinationAssetId,
            isMax = isMax,
            slippageBps = slippageBps,
            quote = quote,
        ),
    )

    /** The snapshot the swap screen passes along, built from a recorded route. */
    fun snapshot(name: String): ConfirmType.Swap.Quote {
        val route = quote(name).routes.first()
        return ConfirmType.Swap.Quote(
            routeId = route.routeId,
            buyAmount = BigInteger.parseString(route.estimatedDestinationAmount),
            minBuyAmount = BigInteger.parseString(route.minimumDestinationAmount),
            provider = route.aggregator.value,
            providerTxId = route.providerRouteId,
            slippageBps = route.totalSlippageBps,
            priceImpactBps = route.valueDifferenceBps,
        )
    }

    private inline fun <reified T> decodeResource(name: String): T {
        val json = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "missing test resource $name"
        }.bufferedReader().use { it.readText() }
        return Serializer.kotlinxSerializationJson.decodeFromString(json)
    }
}
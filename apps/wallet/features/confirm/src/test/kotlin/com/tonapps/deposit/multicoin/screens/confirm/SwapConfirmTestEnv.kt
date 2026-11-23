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
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.PendingSwap
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import java.time.Instant
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
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.fees.NetworkFeesRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import com.tonapps.wallet.testing.testWallet
import io.Serializer
import io.exchangeapi.apis.CrosschainApi
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapConfig
import io.exchangeapi.models.CrossSwapPrepare
import io.exchangeapi.models.CrossSwapPrepareV3
import io.exchangeapi.models.CrossSwapQuote
import io.exchangeapi.models.CrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapQuoteV3
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

    /** `/v3/crosschain` requests: the Generic Payload flow a multichain wallet takes behind its flag. */
    val quoteRequestsV3 = CopyOnWriteArrayList<CrossSwapQuoteRequest>()
    val prepareRequestsV3 = CopyOnWriteArrayList<String>()

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
    val sent = CopyOnWriteArrayList<SentTx>()

    /** What the chain says is left after gas; the default reserves nothing. */
    var reserveAnswer: (ReserveCall) -> NodeRes<GasReserveResult> = { call ->
        NodeRes.ok(GasReserveResult(amount = call.amount, isAmountAdjusted = false, error = null))
    }

    var quoteAnswer: (CreateCrossSwapQuoteRequest) -> CrossSwapQuote = { SwapConfirmFixtures.quote(EXACT_TON) }
    var prepareAnswer: (String) -> CrossSwapPrepare = { SwapConfirmFixtures.prepare(EXACT_TON) }
    var quoteAnswerV3: (CrossSwapQuoteRequest) -> CrossSwapQuoteV3 = { SwapConfirmFixtures.quoteV3(V3_TON_USDT) }
    var prepareAnswerV3: (String) -> CrossSwapPrepareV3 = { SwapConfirmFixtures.prepareV3(V3_TON_USDT) }
    var feeAnswer: (Transaction) -> ChainRes<Fee> = { ChainRes.ok(Fee.None(it.account.chain.toAsset())) }

    // A swap behind an approve is priced at the chain's default limit, not simulated.
    val defaultFeeRequests = CopyOnWriteArrayList<Transaction>()
    var defaultFeeAnswer: (Transaction) -> ChainRes<Fee> = { feeAnswer(it) }

    // The operation confirm of a `/v3` route: every payload priced, held to an intent, signed and sent.

    /** Payloads priced without a fee of their own, by id: chain-kit prices a quoted one as quoted. */
    val payloadFeeRequests = CopyOnWriteArrayList<String>()
    var payloadFeeAnswer: (Payload) -> ChainRes<Fee> = { ChainRes.ok(Fee.Value(it.chain.toAsset(), PAYLOAD_FEE)) }
    val validatedIntents = CopyOnWriteArrayList<Intent>()
    var validationAnswer: (Operation) -> ValidationRes<Unit> = { ValidationRes.ok(Unit) }

    /** Payloads signed, by id, with the nonce each was signed at. */
    val signedPayloads = CopyOnWriteArrayList<Pair<Payload, BigInteger>>()
    val broadcastPayloads = CopyOnWriteArrayList<String>()

    /** Each sent payload's swap report, by payload id; null where it was reported as itself. */
    val reportedSwaps = linkedMapOf<String, PendingSwap?>()

    /** The transaction each sent payload was reported under, by payload id. */
    val reportedHashes = linkedMapOf<String, String>()

    /** The server's clock: before the recorded routes expire. */
    var serverTime: Long = Instant.parse("2026-11-02T15:45:00Z").toEpochMilli()
    var nonceAnswer: () -> NodeRes<BigInteger> = { NodeRes.ok(BigInteger.ONE) }
    var sendAnswer: (SentTx) -> NodeRes<String> = { NodeRes.ok("hash-${sent.size}") }
    var wallet: McWalletEntity = testWallet()
    var feeStateAnswer: (energy: AccountWithDetails, estimated: Fee?) -> TxFeeState = { energy, estimated ->
        TxFeeState(energy = energy, estimated = estimated, options = feeOptions)
    }

    /** Fee methods offered besides the chain's own estimate, a charged battery for one. */
    var feeOptions: List<TxFee> = emptyList()

    /** Where a `/v3` swap's fee methods save the method picked as the wallet's preference. */
    val feeMethodSettings = mockk<SettingsRepository>(relaxed = true)

    /** The swap messages and approvals each fee method build was handed, where a route is made into messages. */
    val feeMethodBuilds = CopyOnWriteArrayList<Pair<List<Transaction.Swap>, List<Transaction>>>()

    /** What a fee method build was handed where the relayer takes the operation's payloads as they are. */
    class OperationBuild(val payloads: List<Payload>, val sold: BigInteger, val shrinkToFit: Boolean)

    val operationBuilds = CopyOnWriteArrayList<OperationBuild>()

    val accounts = linkedMapOf(
        SwapConfirmFixtures.TON_ASSET_ID to SwapConfirmFixtures.tonAccount,
        SwapConfirmFixtures.USDT_TON_ASSET_ID to SwapConfirmFixtures.usdtTonAccount,
        SwapConfirmFixtures.ETH_ASSET_ID to SwapConfirmFixtures.ethAccount,
        SwapConfirmFixtures.USDT_ETH_ASSET_ID to SwapConfirmFixtures.usdtEthAccount,
        SwapConfirmFixtures.BERRY_ASSET_ID to SwapConfirmFixtures.berryAccount,
        SwapConfirmFixtures.HMSTR_ASSET_ID to SwapConfirmFixtures.hmstrAccount,
        SwapConfirmFixtures.BTC_ASSET_ID to SwapConfirmFixtures.btcAccount,
        SwapConfirmFixtures.TRX_ASSET_ID to SwapConfirmFixtures.trxAccount,
        SwapConfirmFixtures.USDT_TRON_ASSET_ID to SwapConfirmFixtures.usdtTronAccount,
    )

    private val crosschain = mockk<CrosschainApi>()
    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val settings = mockk<SettingsRepository>()
    private val legacyAccounts = mockk<LegacySwapAccounts>()
    private val walletUnlocker = mockk<WalletUnlocker>(relaxed = true)

    val ledgerSigner = mockk<LedgerSigningInteractor>(relaxed = true)

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
        every { crosschain.createCrossSwapQuoteV3(any(), any(), any()) } answers {
            val request = firstArg<CrossSwapQuoteRequest>()
            quoteRequestsV3 += request
            requestWalletIds += secondArg<String?>()
            quoteAnswerV3(request)
        }
        every { crosschain.prepareCrossSwapRouteV3(any(), any(), any(), any()) } answers {
            val routeId = firstArg<String>()
            prepareRequestsV3 += routeId
            requestWalletIds += secondArg<String?>()
            prepareAnswerV3(routeId)
        }
        every { crosschain.getCrossSwapConfig(any(), any(), any()) } answers { SwapConfirmFixtures.config }

        every { settings.installId } returns "test-install-id"

        every { api.getBatteryNetworkFees() } returns null

        swapRepo = SwapRepository(api, settings, NetworkFeesRepository(api))

        coEvery { accountRepo.getWallet(any()) } answers { wallet }
        coEvery { accountRepo.getHardwareDescriptor(any()) } returns null
        coEvery { legacyAccounts.find(any()) } answers { legacyWallet }
        coEvery { legacyAccounts.findAccount(any(), any(), any()) } answers { legacyAccountsByAsset[secondArg<String>()] }
        coEvery { legacyAccounts.signingKeys(any()) } answers {
            legacyKeyReads += firstArg<LegacySwapWallet>().id
            SigningWallet.Keys(emptyMap())
        }
        every { legacyAccounts.onTransactionSent(any()) } answers {
            legacySendsNotified += firstArg<LegacySwapWallet>().id
        }
        coEvery { walletUnlocker.confirm(any()) } answers { passcodeConfirmed }
        coEvery { provider.signTransaction(any(), any(), any(), any()) } returns SignRes.ok(SigSet(byteArrayOf(1)))
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } answers {
            accounts[secondArg<String>()]
        }

        coEvery { provider.estimateFee(any<Transaction>()) } answers {
            val transaction = firstArg<Transaction>()
            feeRequests += transaction
            feeAnswer(transaction)
        }
        coEvery { provider.defaultFee(any()) } answers {
            val transaction = firstArg<Transaction>()
            defaultFeeRequests += transaction
            defaultFeeAnswer(transaction)
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
        coEvery { provider.sendTransaction(any(), any(), any(), any(), any(), any(), any()) } answers {
            sentWalletIds += firstArg<String?>()
            val sentTx = SentTx(
                tx = secondArg(),
                data = thirdArg(),
                service = arg(3),
                routeId = arg(4),
                serviceTxId = arg(5),
                sellBaseAmount = arg(6),
            )
            sendAnswer(sentTx).also { result ->
                if (result.error == null) {
                    sent += sentTx
                }
            }
        }
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

        coEvery { feeBuilder.buildSwap(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            feeMethodBuilds += arg<List<Transaction.Swap>>(3) to arg<List<Transaction>>(4)
            feeStateAnswer(arg(1), arg(5))
        }

        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any()) } answers {
            operationBuilds += OperationBuild(payloads = arg(3), sold = arg(4), shrinkToFit = arg(5))
            feeStateAnswer(arg(1), arg(6))
        }

        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            payload.fee?.let { ChainRes.ok(it) } ?: run {
                payloadFeeRequests += payload.id
                payloadFeeAnswer(payload)
            }
        }
        coEvery { provider.validateOperation(any(), any(), any()) } answers {
            validatedIntents += thirdArg<Intent>()
            validationAnswer(firstArg())
        }
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signedPayloads += payload to arg<BigInteger>(3)
            SignRes.ok(SignedPayload.signed(payload, "0xsigned${payload.id}"))
        }
        coEvery { provider.broadcastPayload(any(), any()) } answers {
            val payload = firstArg<SignedPayload>()
            broadcastPayloads += payload.payloadId
            NodeRes.ok("hash-${payload.payloadId}")
        }
        coEvery { provider.reportPayloadSent(any(), any(), any(), any(), any()) } answers {
            reportedSwaps[thirdArg<Payload>().id] = arg(4)
            reportedHashes[thirdArg<Payload>().id] = arg(3)
        }
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns mockk<MnemonicWallet>(relaxed = true)
    }

    // ViewModelStore is the only public way to cancel a ViewModel: clear() itself is internal.
    private val store = ViewModelStore()
    private var featureCount = 0

    val statusTracker = mockk<SwapStatusTracker>(relaxed = true)

    fun feature(request: ConfirmRequest): SwapConfirmFeature = SwapConfirmFeature(
        request = request,
        provider = provider,
        accountRepo = accountRepo,
        postTransactionRefreshSchedule = mockk(relaxed = true),
        swapRepo = swapRepo,
        walletUnlocker = walletUnlocker,
        signatureGate = SignatureGate { false },
        confirmResults = mockk(relaxed = true),
        feeBuilder = feeBuilder,
        gaslessSender = mockk(relaxed = true),
        settingsRepository = mockk(relaxed = true),
        ledgerSigner = ledgerSigner,
        legacyAccounts = legacyAccounts,
        statusTracker = mockk(relaxed = true),
    ).also { store.put("swap-confirm-${featureCount++}", it) }

    fun operationFeature(
        request: ConfirmRequest,
        quoteIntervalMillis: Long = SWAP_QUOTE_INTERVAL_MS,
        feeId: String? = null,
    ): SwapOperationConfirmFeature {
        val operational = request.asSwapOperation()
        return SwapOperationConfirmFeature(
            request = operational,
            provider = provider,
            accountRepo = accountRepo,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = mockk(relaxed = true),
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis(): Long = serverTime
            },
            confirmResults = mockk(relaxed = true),
            swapRepo = swapRepo,
            statusTracker = statusTracker,
            swapQuote = SwapOperationQuote(operational, swapRepo, accountRepo, provider),
            swapFeeMethods = SwapOperationFeeMethods(operational, feeBuilder, feeMethodSettings),
            quoteIntervalMillis = quoteIntervalMillis,
            feeId = feeId,
        ).also { store.put("swap-operation-confirm-${featureCount++}", it) }
    }

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

    class SentTx(
        val tx: Transaction,
        val data: ByteArray,
        val service: String?,
        val routeId: String?,
        val serviceTxId: String?,
        val sellBaseAmount: BigInteger?,
    )

    data class ReserveCall(
        val account: Account,
        val energy: Account,
        val amount: BigInteger,
        val isMax: Boolean,
        val fee: Fee?,
        val policy: GasReservePolicy,
    )

    companion object {
        /** What chain-kit prices a payload at when the route quotes it nothing. */
        val PAYLOAD_FEE: BigInteger = BigInteger.parseString("1500")

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

        const val TON_SPLIT = "ton_split"

        // TON -> HMSTR through Omniston, split across pools: one `ton_boc` payload of four messages,
        // each carrying forward gas on top of its part of the amount. Inline on the quote (10 TON
        // in, 11.14 TON sent), and through prepare (100 TON in, 101.15 TON sent).
        const val OMNISTON_SPLIT = "omniston_ton_hmstr_split"
        const val OMNISTON_SPLIT_PREPARE = "omniston_ton_hmstr_split_prepare"

        // Recorded from `/v3/crosschain` for the test wallet's own addresses.
        // TON -> USDT through Omniston: one TON `main` with a body, forward gas on top of the amount.
        const val V3_TON_USDT = "ton_usdt"

        // USDT on Ethereum -> ETH through swapsxyz: the approve, then the router call, each with its gas.
        const val V3_USDT_ETH = "usdteth_eth"

        // USDT on TRON -> TON through swapsxyz: a TRC20 `transfer` to the deposit address, which only
        // the calldata names.
        const val V3_USDT_TRON = "usdttron_ton"

        // BTC -> ETH through swapsxyz: a plain deposit whose amount the wallet may change.
        const val V3_BTC_ETH = "btc_eth"
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

    val BTC_ASSET_ID = TestAssets.btc.id
    val TRX_ASSET_ID = TestAssets.trx.id
    val USDT_TRON_ASSET_ID = TestAssets.usdtTron.id
    val BTC_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("100000")

    val btcAccount: AccountWithDetails = testAccount(TestAssets.btc, balance = "500000", price = "100000")
    val trxAccount: AccountWithDetails = testAccount(TestAssets.trx, balance = "50000000", price = "0.3")
    val usdtTronAccount: AccountWithDetails = testAccount(TestAssets.usdtTron, balance = "50000000", price = "1.0")

    val config: CrossSwapConfig by lazy { decodeResource("confirm_config.json") }

    fun quote(name: String): CrossSwapQuote = decodeResource("confirm_quote_$name.json")

    fun prepare(name: String): CrossSwapPrepare = decodeResource("confirm_prepare_$name.json")

    fun quoteV3(name: String): CrossSwapQuoteV3 = decodeResource("confirm_v3_quote_$name.json")

    /** The swap screen's snapshot of the first recorded `/v3` route of [name]. */
    fun snapshotV3(name: String): ConfirmType.Swap.Quote {
        val route = quoteV3(name).routes.first()
        return ConfirmType.Swap.Quote(
            routeId = route.routeId,
            buyAmount = BigInteger.parseString(route.estimatedDestinationAmount),
            minBuyAmount = BigInteger.parseString(route.minimumDestinationAmount),
            provider = route.aggregator.value,
            providerTxId = route.providerRouteId,
            slippageBps = route.totalSlippageBps,
            priceImpactBps = route.priceImpactBps,
            genericPayload = true,
        )
    }

    fun prepareV3(name: String): CrossSwapPrepareV3 = decodeResource("confirm_v3_prepare_$name.json")

    /** The confirm request the swap screen hands over, with or without its quote snapshot. */
    fun swapRequest(
        assetId: String = TON_ASSET_ID,
        destinationAssetId: String = USDT_TON_ASSET_ID,
        sourceAmount: BigInteger = TON_SOURCE_AMOUNT,
        isMax: Boolean = false,
        slippageBps: Int? = 100,
        quote: ConfirmType.Swap.Quote? = null,
        oneClick: Boolean = false,
    ) = ConfirmRequest(
        data = CommonTransactionData(assetId = assetId, walletId = WALLET_ID),
        type = ConfirmType.Swap(
            sourceAmount = sourceAmount,
            destinationAssetId = destinationAssetId,
            isMax = isMax,
            slippageBps = slippageBps,
            quote = quote,
            oneClick = oneClick,
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
        return Serializer.JSON.decodeFromString(json)
    }
}

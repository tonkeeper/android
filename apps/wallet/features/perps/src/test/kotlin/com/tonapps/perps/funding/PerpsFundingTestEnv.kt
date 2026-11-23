package com.tonapps.perps.funding

import com.tonapps.blockchain.ton.extensions.toBoc
import com.tonapps.chainkit.core.common.hexWithPrefix
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.chain.ethereum.utils.Hermes
import com.tonapps.blockchain.tron.toEvmHex
import com.tonapps.chainkit.CryptoKitClient
import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.lighter.model.LighterTransferTxReq
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositFeeMethods
import com.tonapps.perps.data.PerpsRealtimeTestEnv
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.perps.security.PerpsSecurityConfigRepository
import com.tonapps.perps.security.PerpsSecurityConfigStore
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsActivation
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.PerpsAPI
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.fees.NetworkFeesRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.SignedLighterTransaction
import com.tonapps.wallet.data.multichain.perps.PerpsFundingDao
import com.tonapps.wallet.data.multichain.perps.PerpsFundingEntity
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.ton.cell.CellBuilder
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The world a perps deposit or withdrawal runs in.
 *
 * Real: both funding services, the quote readers, [PerpsRepository] over its generated API clients,
 * the withdraw tracker and the funding journal.
 *
 * Faked: the perps backend — every HTTP request lands in [requests] and is answered by what a test
 * registered with [answer], so a request nobody expected fails the test — the chain, the wallet's
 * accounts, Lighter's public API and everything that signs for Lighter.
 */
class PerpsFundingTestEnv {

    class Request(val method: String, val path: String, val body: JsonObject?) {
        val endpoint: String
            get() = "$method $path"
    }

    class Answer(val code: Int, val body: String)

    class SignedTransfer(val request: LighterTransferTxReq, val nonce: Long)

    val requests = CopyOnWriteArrayList<Request>()
    val signedTransfers = CopyOnWriteArrayList<SignedTransfer>()

    val endpoints: List<String>
        get() = requests.map { it.endpoint }

    /** What was asked of POST /funding/quote, in order. */
    val quoteBodies: List<JsonObject>
        get() = requests.filter { it.endpoint == QUOTE_DEPOSIT }.map { checkNotNull(it.body) }

    val accounts = linkedMapOf(
        TestAssets.eth.id to testAccount(TestAssets.eth, balance = ONE_ETH, price = "2000"),
        USDC.id to testAccount(USDC, balance = "100000000"),
        TestAssets.usdtEth.id to testAccount(TestAssets.usdtEth, balance = "100000000"),
    )

    var nextNonce = FIRST_LIGHTER_NONCE

    private val answers = mutableMapOf<String, (Request) -> Answer>()
    private val fundingDao = InMemoryFundingDao()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val recorded = Request(
                method = request.method,
                path = request.url.encodedPath,
                body = request.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                },
            )
            requests += recorded
            val answer = answers[recorded.endpoint]?.invoke(recorded)
                ?: throw IllegalStateException("the perps backend got an unexpected ${recorded.endpoint}")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(answer.code)
                .message("")
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .body(answer.body.toResponseBody(JSON_MEDIA_TYPE))
                .build()
        })
        .build()

    private val api = mockk<API>()
    val accountService = mockk<PerpsAccountService>()
    private val perpetualRepository = mockk<PerpetualRepository>()

    // MockK cannot return the value-class KResult from a mock, so deposits quote through a real one.
    private val credentialsStore = mockkClass(Class.forName("com.tonapps.wallet.data.multichain.lighter.LighterCredentialsStore").kotlin, relaxed = true)
    private val depositRepository = PerpetualRepository::class.java.declaredConstructors.single().newInstance(
        credentialsStore,
        mockk<CryptoKitClient>(),
    ) as PerpetualRepository
    private val accountRefresh = PerpsAccountRefresh()

    val mcAccountRepository = mockk<McAccountRepository>()
    val provider = mockk<ChainKitProvider>()
    val feeMethods = mockk<PerpetualDepositFeeMethods>()
    val transactionLookup = mockk<PerpsTransactionLookup>()
    val lighterApi = mockk<LighterPublicApi>()

    // No `/network-fees` answer unless a test gives one: MAX quotes and probes as it did before them.
    val networkFees = mockk<NetworkFeesRepository> {
        every { fees } returns MutableStateFlow(null)
        coEvery { load() } returns null
    }

    val serverTime = FakeTimeProvider()

    private val securityKey = PerpsSecurityBlocks.newKey()
    var servedSecurityBlock: String? = securityBlock()
    var onConfigDownload: () -> Unit = {}
    val securityConfig = PerpsSecurityConfigRepository(
        servedBlock = { servedSecurityBlock },
        store = InMemorySecurityConfigStore(),
        trustedKeys = PerpsSecurityBlocks.trusting(securityKey),
        awaitDownloadedConfig = { onConfigDownload() },
    )

    val repository: PerpsRepository
    val journal = PerpsFundingJournal(fundingDao)
    val tracker: PerpsWithdrawTracker
    val recovery: PerpsFundingRecovery
    val depositService: PerpsDepositService
    val withdrawService: PerpsWithdrawService

    init {
        every { api.perps } returns PerpsAPI(BASE_URL, httpClient)
        repository = PerpsRepository(api, mockk<PerpsCandleFeed>(), mockk<EnvironmentHelper>(), mockk(relaxed = true))

        coEvery { mcAccountRepository.getWallet(WALLET_ID) } returns testWallet()
        coEvery { feeMethods.build(any(), any(), any(), any()) } answers { TxFeeState(energy = thirdArg(), estimated = arg(3)) }
        coEvery { feeMethods.canRelayShortfall(any(), any(), any(), any()) } returns false
        every { feeMethods.payment(any()) } returns null
        every { feeMethods.buildsUnpriced } returns false
        coEvery { mcAccountRepository.getCoinAccounts(WALLET_ID) } answers {
            accounts.values.filter { it.asset.id.endsWith("/coin") }.map { it.data }
        }
        coEvery { mcAccountRepository.findAccount(WALLET_ID, any(), any(), any()) } answers {
            account(secondArg())
        }
        coEvery { mcAccountRepository.getWalletKeyPair(WALLET_ID) } answers {
            WalletKeyPair.fromPrivateKey(ByteArray(PRIVATE_KEY_SIZE) { 1 }, WalletKind.Multichain)
        }
        every { mcAccountRepository.refresh() } just runs

        coEvery { provider.getNonce(any()) } returns NodeRes.ok(ChainInteger.fromLong(EVM_NONCE))
        coEvery { provider.transactionHash(any()) } answers { "txid-" + firstArg<SignedPayload>().payloadId }

        coEvery { lighterApi.assetMinimums(PerpsWithdrawService.USDC_ASSET_INDEX) } returns
            LighterAssetMinimums(transfer = BigDecimal.ONE)
        coEvery { lighterApi.transferFeeUnits(ACCOUNT_INDEX, any()) } returns TRANSFER_FEE_UNITS

        val credentials = mockk<LighterSigningCredentials>()
        every { credentials.accountIndex } returns ACCOUNT_INDEX
        every { credentials.chainId } returns LIGHTER_CHAIN_ID
        coEvery { accountService.ensureActivated(WALLET_ID, any()) } returns PerpsActivation.Ready(credentials)
        coEvery { accountService.signOwnerMessage(any(), any()) } returns "0xowner"
        coEvery { accountService.keptTradingPublicKey(any()) } returns TRADING_PUBLIC_KEY
        coEvery { accountService.reissueRoToken(WALLET_ID) } returns true
        coEvery { accountService.activateWithKeptKey(WALLET_ID) } returns false

        coEvery { perpetualRepository.signTransfer(WALLET_ID, any(), any(), any(), any()) } answers {
            val nonce = arg<Long>(NONCE_OF_TRANSFER)
            signedTransfers += SignedTransfer(request = secondArg(), nonce = nonce)
            signed(TX_TYPE_TRANSFER, nonce, expiredAtMs = arg(EXPIRY_OF_TRANSFER))
        }

        tracker = PerpsWithdrawTracker(repository, journal, mcAccountRepository, lighterApi, accountRefresh, serverTime)
        // Its loop never starts on a cancelled scope: nothing moves a record behind a test's back.
        recovery = PerpsFundingRecovery(
            journal = journal,
            registrar = mockk(),
            repository = repository,
            transactionLookup = transactionLookup,
            accountRefresh = accountRefresh,
            unifiedAccountRepository = mockk(),
            withdrawTracker = tracker,
            realtime = PerpsRealtimeTestEnv(CoroutineScope(Job().apply { cancel() })).realtime,
            scope = CoroutineScope(Job().apply { cancel() }),
        )
        depositService = PerpsDepositService(
            repository = repository,
            journal = journal,
            mcAccountRepository = mcAccountRepository,
            settingsRepository = mockk(),
            accountService = accountService,
            provider = provider,
            recovery = recovery,
            perpetualRepository = depositRepository,
            feeMethods = feeMethods,
            timeProvider = serverTime,
            networkFees = networkFees,
            securityConfig = securityConfig,
        )
        withdrawService = PerpsWithdrawService(
            repository = repository,
            journal = journal,
            mcAccountRepository = mcAccountRepository,
            accountService = accountService,
            perpetualRepository = perpetualRepository,
            lighterApi = lighterApi,
            tracker = tracker,
            recovery = recovery,
            timeProvider = serverTime,
            securityConfig = securityConfig,
        )

        answer(GET_ACCOUNT) {
            ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("l1_address", L1_ADDRESS)
                },
            )
        }
        answer(GET_PORTFOLIO) {
            ok(buildJsonObject { putJsonObject("balance") { put("transferable_balance", "100") } })
        }
        answer(GET_NEXT_NONCE) {
            ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("nonce", nextNonce++)
                },
            )
        }
        answer(SEND_TRANSACTIONS) { request -> ok(accepted(request)) }
    }

    fun answer(endpoint: String, answer: (Request) -> Answer) {
        answers[endpoint] = answer
    }

    /** A wallet whose [TRADING_PUBLIC_KEY] was never kept, as creation, import and every unlock keep it. */
    fun withoutAccount() {
        answer(GET_ACCOUNT) {
            ok(
                buildJsonObject {
                    put("status", "not_registered")
                    put("l1_address", L1_ADDRESS)
                },
            )
        }
    }

    fun securityBlock(
        delegate: String = HERMES_DELEGATE,
        relayDepositories: List<Pair<String, JsonPrimitive>>? = listOf(
            "lighter" to JsonPrimitive(AGGREGATOR_ACCOUNT_INDEX.toString()),
            "ton" to JsonPrimitive(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY),
            "eth" to JsonPrimitive(RELAY_DEPOSITORY),
            "arb" to JsonPrimitive(RELAY_DEPOSITORY),
            "bsc" to JsonPrimitive(RELAY_DEPOSITORY),
            "tron" to JsonPrimitive(TRON_RELAY_DEPOSITORY),
            "btc" to JsonPrimitive(PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY),
        ),
        relayRouters: List<JsonObject>? = PerpsSecurityBlocks.relayRouters("eth", "arb", "bsc"),
    ): String {
        return PerpsSecurityBlocks.signed(
            securityKey,
            PerpsSecurityBlocks.payload(delegate = delegate, relayDepositories = relayDepositories, relayRouters = relayRouters),
        )
    }

    fun signedSecurityBlock(payload: JsonObject): String = PerpsSecurityBlocks.signed(securityKey, payload)

    fun withoutKeptKey() {
        coEvery { accountService.keptTradingPublicKey(WALLET_ID) } returns null
    }

    fun source(assetId: String): PerpsDepositSource {
        val account = account(assetId) ?: error("no account for $assetId")
        return PerpsDepositSource(account, PerpsFundingAssetId.parse(assetId) ?: error("$assetId cannot fund a deposit"))
    }

    /** Answers a deposit quote for whatever is asked, paid with [payloads]. */
    fun quoteDeposits(
        provider: String,
        credited: BigInteger,
        executionId: String?,
        expiresAtMs: Long = System.currentTimeMillis() + QUOTE_TTL_MS,
        operationId: String? = DEPOSIT_OPERATION_ID,
        estimatedDurationSeconds: Int? = null,
        accountUninitialized: Boolean? = null,
        fees: List<Pair<String, String>> = emptyList(),
        refusal: (asked: JsonObject) -> Answer? = { null },
        preview: ((asked: JsonObject) -> Boolean)? = null,
        payloads: (asked: JsonObject) -> List<JsonObject>,
    ) {
        answer(QUOTE_DEPOSIT) { request ->
            val asked = checkNotNull(request.body)
            refusal(asked)?.let { return@answer it }
            val operation = payloads(asked)
            ok(
                buildJsonObject {
                    put("quote_id", "deposit-quote-1")
                    put("provider", provider)
                    put("created_at", Instant.ofEpochMilli(expiresAtMs - QUOTE_TTL_MS).toString())
                    put("expires_at", Instant.ofEpochMilli(expiresAtMs).toString())
                    putJsonObject("input") {
                        put("asset_id", asked.string("source_asset"))
                        put("amount", asked.string("source_amount"))
                        put("address", asked.string("sender_address"))
                    }
                    putJsonObject("output") {
                        put("asset_id", PerpsFundingAssetId.USDC_ETHEREUM)
                        put("amount", credited.toString())
                        put("min_amount", credited.toString())
                        put("address", asked.string("destination_address"))
                    }
                    putJsonObject("operation") {
                        if (operationId != null) {
                            put("id", operationId)
                        }
                        putJsonArray("payloads") { operation.forEach(::add) }
                    }
                    // As the service answers: a preview's operation is the Hermes Execute and its authorization.
                    put("preview", preview?.invoke(asked) ?: operation.any { it.string("type") == "eip7821/hermes/delegate" })
                    if (estimatedDurationSeconds != null) {
                        put("estimated_duration_seconds", estimatedDurationSeconds)
                    }
                    if (accountUninitialized != null) {
                        put("account_uninitialized", accountUninitialized)
                    }
                    if (fees.isNotEmpty()) {
                        putJsonArray("fees") {
                            fees.forEach { (type, amountUsd) ->
                                add(
                                    buildJsonObject {
                                        put("type", type)
                                        put("amount_usd", amountUsd)
                                    },
                                )
                            }
                        }
                    }
                    if (executionId != null) {
                        putJsonObject("tracking") {
                            put("execution_id", executionId)
                            put("status_url", "$BASE_URL/funding/status/$executionId")
                        }
                    }
                },
            )
        }
    }

    /** Answers a withdraw quote for whatever is asked: one Lighter transfer to the aggregator's account. */
    fun quoteWithdrawals(
        received: BigInteger,
        expiresAtMs: Long = System.currentTimeMillis() + QUOTE_TTL_MS,
        lifetimeMs: Long = QUOTE_TTL_MS,
        transferFeeUnits: BigInteger = TRANSFER_FEE_UNITS,
        estimatedDurationSeconds: Int? = null,
        toAccountIndex: Long = AGGREGATOR_ACCOUNT_INDEX,
        unspentUnits: BigInteger = BigInteger.ZERO,
    ) {
        answer(QUOTE_WITHDRAW) { request ->
            val asked = checkNotNull(request.body)
            val amount = BigInteger(asked.string("amount"))
            ok(
                buildJsonObject {
                    put("quote_id", "withdraw-quote-1")
                    put("provider", "relay")
                    put("created_at", Instant.ofEpochMilli(expiresAtMs - lifetimeMs).toString())
                    put("expires_at", Instant.ofEpochMilli(expiresAtMs).toString())
                    putJsonObject("input") {
                        put("asset_id", PerpsFundingAssetId.USDC_ETHEREUM)
                        put("amount", amount.toString())
                        put("account_index", ACCOUNT_INDEX)
                    }
                    putJsonObject("output") {
                        put("asset_id", asked.string("destination_asset"))
                        put("amount", received.toString())
                        put("min_amount", received.toString())
                        put("address", asked.string("recipient_address"))
                    }
                    putJsonObject("operation") {
                        put("id", "withdraw-operation-1")
                        put("type", "transfer")
                        putJsonArray("payloads") {
                            add(
                                buildJsonObject {
                                    put("id", "transfer-1")
                                    put("type", "main")
                                    put("chain_id", "lighter/mainnet")
                                    put("version", 1)
                                    putJsonObject("meta") {
                                        put("to_account_index", toAccountIndex)
                                        put("asset_index", PerpsWithdrawService.USDC_ASSET_INDEX)
                                        put("from_route_type", 0)
                                        put("to_route_type", 0)
                                        put("amount", (amount - transferFeeUnits - unspentUnits).toString())
                                        put("usdc_fee", transferFeeUnits.toString())
                                        put("memo", TRANSFER_MEMO)
                                    }
                                },
                            )
                        }
                    }
                    putJsonObject("tracking") {
                        put("execution_id", WITHDRAW_EXECUTION_ID)
                        put("status_url", "$BASE_URL/funding/status/$WITHDRAW_EXECUTION_ID")
                    }
                    if (estimatedDurationSeconds != null) {
                        put("estimated_duration_seconds", estimatedDurationSeconds)
                    }
                },
            )
        }
    }

    fun tronPayload(
        id: String,
        to: String,
        data: String,
        value: BigInteger = BigInteger.ZERO,
        fee: BigInteger? = null,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "main")
        put("chain_id", "tron/mainnet")
        put("version", 1)
        if (fee != null) {
            putJsonObject("fee") {
                put("type", "value")
                put("asset_id", "tron/mainnet/coin")
                putJsonObject("data") { put("amount", fee.toString()) }
            }
        }
        putJsonObject("meta") {
            put("to", to)
            put("value", value.toString())
            put("data", data)
        }
    }

    fun tonPayload(
        id: String,
        to: String = PerpsSecurityBlocks.RELAY_TON_DEPOSITORY_FRIENDLY,
        value: BigInteger,
        body: String? = tonComment(RELAY_ORDER_ID),
        mode: Int? = TON_SEND_MODE,
        type: String = "main",
        chain: String = "ton/mainnet",
        meta: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type)
        put("chain_id", chain)
        put("version", 1)
        put(
            "meta",
            JsonObject(
                buildJsonObject {
                    put("to", to)
                    put("value", value.toString())
                    if (body != null) {
                        put("body", body)
                    }
                    put("bounce", false)
                    if (mode != null) {
                        put("mode", mode)
                    }
                } + meta,
            ),
        )
    }

    fun tonComment(text: String): String = tonBody(op = 0) { storeBytes(text.toByteArray()) }

    fun tonBody(op: Int, refs: Int = 0, tail: CellBuilder.() -> Unit = {}): String {
        val cell = CellBuilder.createCell {
            storeUInt(op, OP_BITS)
            tail()
            repeat(refs) { storeRef(CellBuilder.createCell { storeUInt(0, OP_BITS) }) }
        }
        return Base64.getEncoder().encodeToString(cell.toBoc())
    }

    fun bitcoinFeeRate(satsPerByte: Long) {
        coEvery { provider.estimateFee(match<Payload> { it.meta is PayloadMeta.Bitcoin.Main }, any(), any()) } returns ChainRes.ok(
            Fee.Gas(Chain.Bitcoin.Mainnet.toAsset(), limit = ChainInteger.fromInt(141), price = ChainInteger.fromLong(satsPerByte)),
        )
    }

    fun bitcoinPayload(id: String, psbt: String): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "prebuild")
        put("chain_id", "btc/mainnet")
        put("version", 1)
        putJsonObject("meta") { put("data", psbt) }
    }

    fun evmPayload(
        id: String,
        to: String,
        value: BigInteger = BigInteger.ZERO,
        data: String = "0x",
        authorizationNonce: Long? = null,
        authorizationAddress: String = HERMES_DELEGATE,
        authorizationChainId: Long = 1,
        chain: String = "eth/mainnet",
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "main")
        put("chain_id", chain)
        put("version", 1)
        putJsonObject("meta") {
            put("to", to)
            put("value", value.toString())
            put("data", data)
            put("gas", "210000")
            if (authorizationNonce != null) {
                putJsonObject("authorization") {
                    put("chainId", authorizationChainId)
                    put("address", authorizationAddress)
                    put("nonce", authorizationNonce)
                }
            }
        }
    }

    /** A change_pub_key preview's delegation of the owner's address to Hermes, to sign. */
    fun authorizationPayload(
        id: String,
        address: String = HERMES_DELEGATE,
        chainId: Long = 1,
        chain: String = "eth/mainnet",
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "eip7702/authorization")
        put("chain_id", chain)
        put("version", 1)
        putJsonObject("meta") {
            put("chainId", chainId)
            put("address", address)
            put("nonce", EVM_NONCE)
        }
    }

    /** A change_pub_key preview's Execute of changePubKey through the delegated address, to sign. */
    fun hermesPayload(id: String, document: JsonObject = hermesDocument()): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "eip7821/hermes/delegate")
        put("chain_id", "eth/mainnet")
        put("version", 1)
        putJsonObject("meta") { put("data", document) }
    }

    fun hermesDocument(
        owner: String = L1_ADDRESS,
        salt: String = "0x" + word(HERMES_DELEGATE),
        mode: String = SIGNED_BATCH_MODE,
        calls: List<JsonObject> = listOf(hermesCall()),
        deadline: Long = serverTime.currentTimeSeconds() + HERMES_DEADLINE_SECONDS,
        nonce: String = HERMES_NONCE.toString(),
        domainType: List<Pair<String, String>>? = null,
        callType: List<Pair<String, String>> = listOf("target" to "address", "value" to "uint256", "data" to "bytes"),
    ): JsonObject = buildJsonObject {
        putJsonObject("types") {
            if (domainType != null) {
                put("EIP712Domain", typeFields(domainType))
            }
            put("Call", typeFields(callType))
            put("Execute", typeFields(listOf("mode" to "bytes32", "calls" to "Call[]", "nonce" to "uint256", "deadline" to "uint256")))
        }
        put("primaryType", "Execute")
        putJsonObject("domain") {
            put("name", "Hermes")
            put("version", "v1.0.0")
            put("chainId", 1)
            put("verifyingContract", owner)
            put("salt", salt)
        }
        putJsonObject("message") {
            put("mode", mode)
            put("calls", JsonArray(calls))
            put("nonce", nonce)
            put("deadline", deadline.toString())
        }
    }

    fun hermesCall(
        target: String = CHANGE_PUB_KEY_CONTRACT,
        value: String = "0",
        data: String = registrationCalldata(TRADING_PUBLIC_KEY),
    ): JsonObject = buildJsonObject {
        put("target", target)
        put("value", value)
        put("data", data)
    }

    fun registrationCalldata(publicKey: String): String {
        return "0x17010c68" +
            word(BigInteger.valueOf(281_474_976_710_655L)) +
            word(BigInteger.valueOf(3L)) +
            word(BigInteger.valueOf(0x60L)) +
            word(BigInteger.valueOf(40L)) +
            publicKey.removePrefix("0x") + "0".repeat(48)
    }

    private fun typeFields(fields: List<Pair<String, String>>): JsonArray = buildJsonArray {
        fields.forEach { (name, type) ->
            addJsonObject {
                put("name", name)
                put("type", type)
            }
        }
    }

    fun approveData(spender: String, amount: BigInteger): String = "0x095ea7b3" + word(spender) + word(amount)

    fun nativeDepositData(depositor: String = L1_ADDRESS): String = "0x49290c1c" + word(depositor) + RELAY_REQUEST_ID

    fun tokenDepositData(token: String, amount: BigInteger, depositor: String = L1_ADDRESS): String {
        return "0xe8017952" + word(depositor) + word(token) + word(amount) + RELAY_REQUEST_ID
    }

    fun tronNativeDepositData(depositor: String = TestWallet.TRON_ADDRESS): String = "49290c1c" + word(tronHex(depositor)) + RELAY_REQUEST_ID

    fun tronApproveData(spender: String, amount: BigInteger): String = "095ea7b3" + word(tronHex(spender)) + word(amount)

    fun tronTokenDepositData(token: String, amount: BigInteger, depositor: String = TestWallet.TRON_ADDRESS): String {
        return "e8017952" + word(tronHex(depositor)) + word(tronHex(token)) + word(amount) + RELAY_REQUEST_ID
    }

    fun batchData(vararg calls: Triple<String, BigInteger, String>, chain: Chain = Chain.Ethereum.Mainnet): String {
        val batch = calls.map { (target, value, data) ->
            Hermes.Call(Address.force(target, chain), ChainInteger.parseString(value.toString()), data)
        }
        return Hermes.execute(Hermes.mode(), Hermes.encodeCalls(batch)).hexWithPrefix()
    }

    private fun tronHex(address: String): String = "0x" + address.toEvmHex()

    fun gatewayDepositData(beneficiary: String, amount: BigInteger): String {
        return "0x8a857083" + word(beneficiary) + word(GATEWAY_USDC_ASSET_INDEX) + word(BigInteger.ZERO) + word(amount)
    }

    fun ok(body: JsonObject) = Answer(HTTP_OK, body.toString())

    fun refused(
        status: Int,
        code: String,
        reason: String?,
        retryable: Boolean = false,
        minUsdAmount: String? = null,
    ) = Answer(
        code = status,
        body = buildJsonObject {
            put("code", code)
            put("retryable", retryable)
            if (reason != null || minUsdAmount != null) {
                putJsonObject("details") {
                    reason?.let { put("reason", it) }
                    minUsdAmount?.let { put("min_usd_amount", it) }
                }
            }
        }.toString(),
    )

    /** What the backend answers a send with: every transaction accepted under the hash it was signed with. */
    fun accepted(request: Request): JsonObject = buildJsonObject {
        putJsonArray("transactions") {
            checkNotNull(request.body).getValue("transactions").jsonArray.forEach { transaction ->
                val sent = transaction.jsonObject
                add(
                    buildJsonObject {
                        put("tx_type", sent.getValue("tx_type"))
                        put("tx_hash", Json.parseToJsonElement(sent.string("tx_info")).jsonObject.string("Hash"))
                    },
                )
            }
        }
    }

    private fun account(assetId: String): AccountWithDetails? {
        return accounts.entries.firstOrNull { it.key.equals(assetId, ignoreCase = true) }?.value
    }

    private fun signed(txType: Int, nonce: Long, expiredAtMs: Long): SignedLighterTransaction {
        val signing = signedTransfers.size
        val hash = "0x" + "$signing$nonce".padStart(HASH_HEX_LENGTH, '0')
        return SignedLighterTransaction(
            txType = txType,
            txInfo = buildJsonObject {
                put("Nonce", nonce)
                put("ExpiredAt", expiredAtMs)
                put("Hash", hash)
            }.toString(),
            txHash = hash,
            nonce = nonce,
            expiredAtMs = expiredAtMs,
        )
    }

    private fun word(address: String): String = address.lowercase().removePrefix("0x").padStart(ABI_WORD_LENGTH, '0')

    private fun word(value: BigInteger): String = value.toString(HEX_RADIX).padStart(ABI_WORD_LENGTH, '0')

    class InMemorySecurityConfigStore : PerpsSecurityConfigStore {

        var block: String? = null

        override fun keptBlock(): String? = block

        override fun keep(block: String) {
            this.block = block
        }
    }

    private class InMemoryFundingDao : PerpsFundingDao {

        private val rows = LinkedHashMap<String, PerpsFundingEntity>()
        private val changes = MutableStateFlow(0)

        override suspend fun insert(operation: PerpsFundingEntity): Long {
            if (operation.operationId in rows) {
                return IGNORED_ROW_ID
            }
            rows[operation.operationId] = operation
            changes.update { it + 1 }
            return rows.size.toLong()
        }

        override suspend fun update(operation: PerpsFundingEntity): Int {
            if (operation.operationId !in rows) {
                return 0
            }
            rows[operation.operationId] = operation
            changes.update { it + 1 }
            return 1
        }

        override suspend fun get(operationId: String): PerpsFundingEntity? = rows[operationId]

        override suspend fun getOperations(walletId: String): List<PerpsFundingEntity> {
            return rows.values.filter { it.walletId == walletId }.sortedBy { it.createdAt }
        }

        override fun observeOperations(walletId: String): Flow<List<PerpsFundingEntity>> {
            return changes.map { rows.values.filter { it.walletId == walletId }.sortedBy { it.createdAt } }
        }

        override suspend fun getAll(): List<PerpsFundingEntity> = rows.values.toList()

        override suspend fun delete(operationIds: List<String>) {
            operationIds.forEach(rows::remove)
            changes.update { it + 1 }
        }

        override suspend fun acknowledge(operationIds: List<String>) {
            operationIds.forEach { id -> rows[id]?.let { rows[id] = it.copy(statusAcknowledged = true) } }
            changes.update { it + 1 }
        }

        private companion object {
            const val IGNORED_ROW_ID = -1L
        }
    }

    companion object {
        const val BASE_URL = "https://perps.test"
        val WALLET_ID = TestWallet.ID

        const val QUOTE_DEPOSIT = "POST /funding/quote"
        const val QUOTE_WITHDRAW = "POST /funding/withdraw/quote"
        const val SEND_TRANSACTIONS = "POST /transactions/send"
        const val GET_NEXT_NONCE = "GET /transactions/next-nonce"
        const val GET_ACCOUNT = "GET /account"
        const val GET_PORTFOLIO = "GET /portfolio"
        const val WITHDRAW_EXECUTION_ID = "withdraw-execution-1"
        const val GET_WITHDRAW_STATUS = "GET /funding/status/$WITHDRAW_EXECUTION_ID"

        const val DEPOSIT_OPERATION_ID = "deposit-operation-1"
        const val DEPOSIT_EXECUTION_ID = "deposit-execution-1"
        const val GATEWAY = "0x3b4d794a66304f130a4db8f2551b0070dfcf5ca7"
        const val CHANGE_PUB_KEY_CONTRACT = "0x183ef7195d443a3da7ec1d7003baca1ff490757d"
        const val RELAY_DEPOSITORY = "0x4cd00e387622c35bddb9b4c962c136462338bc31"
        const val TRON_RELAY_DEPOSITORY = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val RELAY_REQUEST_ID = "2e3492d569b1bb949ee126b3515f7e32e709a15dbfc6342022214e2f9e95fefb"
        const val HERMES_DELEGATE = "0x24b576bc271823bf9c24be627b4363b9b00191e9"
        const val USDC_TOKEN = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        /** The wallet's Ethereum address as the perps backend spells it: what a deposit credits and a withdrawal pays. */
        val L1_ADDRESS = TestWallet.ETH_ADDRESS.lowercase()
        val USDC = testAsset(PerpsFundingAssetId.USDC_ETHEREUM, symbol = "USDC", name = "USD Coin", decimals = 6)

        val TRADING_PUBLIC_KEY = "0x" + "11".repeat(40)
        const val HERMES_NONCE = 3L
        const val HERMES_DEADLINE_SECONDS = 60L * 60L
        const val SIGNED_BATCH_MODE = "0x0100000000007821000100000000000000000000000000000000000000000000"

        const val ACCOUNT_INDEX = 738_887L
        const val AGGREGATOR_ACCOUNT_INDEX = 281_474_976_710_648L
        const val OTHER_LIGHTER_ACCOUNT = 281_474_976_710_649L
        const val FIRST_LIGHTER_NONCE = 41L
        const val EVM_NONCE = 7L
        const val TX_TYPE_TRANSFER = 12
        const val QUOTE_TTL_MS = 60_000L
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAVAILABLE = 503
        val TRANSFER_FEE_UNITS: BigInteger = BigInteger.valueOf(100_000L)
        val TRANSFER_MEMO = "0x" + "ab".repeat(32)
        val RELAY_ORDER_ID = "0x" + "fd0f32a9a6b39a61580f435569f664b7d8ea58cbff16f6af18a76a14f62527c5"
        const val TON_SEND_MODE = 3

        private const val ONE_ETH = "1000000000000000000"
        private const val LIGHTER_CHAIN_ID = 304
        private const val PRIVATE_KEY_SIZE = 32
        private const val HASH_HEX_LENGTH = 80
        private const val ABI_WORD_LENGTH = 64
        private const val HEX_RADIX = 16
        private const val OP_BITS = 32
        private const val NONCE_OF_TRANSFER = 2
        private const val EXPIRY_OF_TRANSFER = 3
        private val GATEWAY_USDC_ASSET_INDEX: BigInteger = BigInteger.valueOf(3)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

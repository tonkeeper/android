package com.tonapps.perps.funding

import com.tonapps.chainkit.CryptoKitClient
import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.lighter.model.LighterTransferTxReq
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsActivation
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.PerpsAPI
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.SignedLighterTransaction
import com.tonapps.wallet.data.multichain.perps.PerpsFundingDao
import com.tonapps.wallet.data.multichain.perps.PerpsFundingEntity
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
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

    class SignedWithdrawal(val amountUnits: Long, val nonce: Long)

    class SignedTransfer(val request: LighterTransferTxReq, val nonce: Long)

    val requests = CopyOnWriteArrayList<Request>()
    val signedWithdrawals = CopyOnWriteArrayList<SignedWithdrawal>()
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
    val transactionLookup = mockk<PerpsTransactionLookup>()
    val lighterApi = mockk<LighterPublicApi>()

    val repository: PerpsRepository
    val journal = PerpsFundingJournal(fundingDao)
    val tracker: PerpsWithdrawTracker
    val recovery: PerpsFundingRecovery
    val depositService: PerpsDepositService
    val withdrawService: PerpsWithdrawService

    init {
        every { api.perps } returns PerpsAPI(BASE_URL, httpClient)
        repository = PerpsRepository(api, mockk<PerpsCandleFeed>(), mockk<EnvironmentHelper>())

        coEvery { mcAccountRepository.getWallet(WALLET_ID) } returns testWallet()
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
            LighterAssetMinimums(withdrawal = BigDecimal.ONE, transfer = BigDecimal.ONE)
        coEvery { lighterApi.withdrawalDelaySeconds() } returns WITHDRAWAL_DELAY_SECONDS
        coEvery { lighterApi.transferFeeUnits(ACCOUNT_INDEX, any()) } returns TRANSFER_FEE_UNITS

        val credentials = mockk<LighterSigningCredentials>()
        every { credentials.accountIndex } returns ACCOUNT_INDEX
        every { credentials.chainId } returns LIGHTER_CHAIN_ID
        coEvery { accountService.ensureActivated(WALLET_ID, any()) } returns PerpsActivation.Ready(credentials)
        coEvery { accountService.signOwnerMessage(any(), any()) } returns "0xowner"
        coEvery { accountService.keptTradingPublicKey(any()) } returns TRADING_PUBLIC_KEY
        coEvery { accountService.reissueRoToken(WALLET_ID) } returns true
        coEvery { accountService.activateWithKeptKey(WALLET_ID) } returns false

        coEvery { perpetualRepository.signWithdraw(WALLET_ID, any(), any(), any(), any(), any()) } answers {
            val nonce = arg<Long>(NONCE_OF_WITHDRAW)
            signedWithdrawals += SignedWithdrawal(amountUnits = arg(AMOUNT_OF_WITHDRAW), nonce = nonce)
            signed(TX_TYPE_WITHDRAW, nonce, expiredAtMs = arg(EXPIRY_OF_WITHDRAW))
        }
        coEvery { perpetualRepository.signTransfer(WALLET_ID, any(), any(), any(), any()) } answers {
            val nonce = arg<Long>(NONCE_OF_TRANSFER)
            signedTransfers += SignedTransfer(request = secondArg(), nonce = nonce)
            signed(TX_TYPE_TRANSFER, nonce, expiredAtMs = arg(EXPIRY_OF_TRANSFER))
        }

        tracker = PerpsWithdrawTracker(repository, journal, mcAccountRepository, lighterApi, accountRefresh)
        // Its loop never starts on a cancelled scope: nothing moves a record behind a test's back.
        recovery = PerpsFundingRecovery(
            journal = journal,
            registrar = mockk(),
            repository = repository,
            transactionLookup = transactionLookup,
            accountRefresh = accountRefresh,
            unifiedAccountRepository = mockk(),
            withdrawTracker = tracker,
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
        refusal: (asked: JsonObject) -> Answer? = { null },
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
                    put("preview", operation.any { it.string("type") == "eip7821/hermes/delegate" })
                    if (estimatedDurationSeconds != null) {
                        put("estimated_duration_seconds", estimatedDurationSeconds)
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
        estimatedDurationSeconds: Int? = null,
    ) {
        answer(QUOTE_WITHDRAW) { request ->
            val asked = checkNotNull(request.body)
            val amount = BigInteger(asked.string("amount"))
            ok(
                buildJsonObject {
                    put("quote_id", "withdraw-quote-1")
                    put("provider", "relay")
                    put("created_at", Instant.ofEpochMilli(expiresAtMs - QUOTE_TTL_MS).toString())
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
                                        put("to_account_index", AGGREGATOR_ACCOUNT_INDEX)
                                        put("asset_index", PerpsWithdrawService.USDC_ASSET_INDEX)
                                        put("from_route_type", 0)
                                        put("to_route_type", 0)
                                        put("amount", (amount - TRANSFER_FEE_UNITS).toString())
                                        put("usdc_fee", TRANSFER_FEE_UNITS.toString())
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

    fun tronPayload(id: String, to: String, data: String, value: BigInteger = BigInteger.ZERO): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "main")
        put("chain_id", "tron/mainnet")
        put("version", 1)
        putJsonObject("meta") {
            put("to", to)
            put("value", value.toString())
            put("data", data)
        }
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
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "main")
        put("chain_id", "eth/mainnet")
        put("version", 1)
        putJsonObject("meta") {
            put("to", to)
            put("value", value.toString())
            put("data", data)
            put("gas", "210000")
            if (authorizationNonce != null) {
                putJsonObject("authorization") {
                    put("chainId", 1)
                    put("address", HERMES_DELEGATE)
                    put("nonce", authorizationNonce)
                }
            }
        }
    }

    /** A change_pub_key preview's delegation of the owner's address to Hermes, to sign. */
    fun authorizationPayload(id: String): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "eip7702/authorization")
        put("chain_id", "eth/mainnet")
        put("version", 1)
        putJsonObject("meta") {
            put("chainId", 1)
            put("address", HERMES_DELEGATE)
            put("nonce", EVM_NONCE)
        }
    }

    /** A change_pub_key preview's Execute of changePubKey through the delegated address, to sign. */
    fun hermesPayload(id: String): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "eip7821/hermes/delegate")
        put("chain_id", "eth/mainnet")
        put("version", 1)
        putJsonObject("meta") {
            putJsonObject("data") {
                putJsonObject("types") {
                    putJsonArray("Call") {
                        add(typeField("target", "address"))
                        add(typeField("value", "uint256"))
                        add(typeField("data", "bytes"))
                    }
                    putJsonArray("Execute") {
                        add(typeField("mode", "bytes32"))
                        add(typeField("calls", "Call[]"))
                        add(typeField("nonce", "uint256"))
                        add(typeField("deadline", "uint256"))
                    }
                }
                put("primaryType", "Execute")
                putJsonObject("domain") {
                    put("name", "Hermes")
                    put("version", "v1.0.0")
                    put("chainId", 1)
                    put("verifyingContract", L1_ADDRESS)
                    put("salt", "0x" + word(HERMES_DELEGATE))
                }
                putJsonObject("message") {
                    put("mode", "0x0100000000007821000100000000000000000000000000000000000000000000")
                    putJsonArray("calls") {
                        add(
                            buildJsonObject {
                                put("target", GATEWAY)
                                put("value", "0")
                                put("data", "0x")
                            },
                        )
                    }
                    put("nonce", HERMES_NONCE.toString())
                    put("deadline", HERMES_DEADLINE.toString())
                }
            }
        }
    }

    private fun typeField(name: String, type: String): JsonObject = buildJsonObject {
        put("name", name)
        put("type", type)
    }

    fun approveData(spender: String, amount: BigInteger): String = "0x095ea7b3" + word(spender) + word(amount)

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
        val hash = "0x" + nonce.toString().padStart(HASH_HEX_LENGTH, '0')
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

    private class InMemoryFundingDao : PerpsFundingDao {

        private val rows = LinkedHashMap<String, PerpsFundingEntity>()

        override suspend fun insert(operation: PerpsFundingEntity): Long {
            if (operation.operationId in rows) {
                return IGNORED_ROW_ID
            }
            rows[operation.operationId] = operation
            return rows.size.toLong()
        }

        override suspend fun update(operation: PerpsFundingEntity): Int {
            if (operation.operationId !in rows) {
                return 0
            }
            rows[operation.operationId] = operation
            return 1
        }

        override suspend fun get(operationId: String): PerpsFundingEntity? = rows[operationId]

        override suspend fun getOperations(walletId: String): List<PerpsFundingEntity> {
            return rows.values.filter { it.walletId == walletId }.sortedBy { it.createdAt }
        }

        override fun observeOperations(walletId: String): Flow<List<PerpsFundingEntity>> {
            return flowOf(rows.values.filter { it.walletId == walletId })
        }

        override suspend fun getAll(): List<PerpsFundingEntity> = rows.values.toList()

        override suspend fun delete(operationIds: List<String>) {
            operationIds.forEach(rows::remove)
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
        const val RELAY_DEPOSITORY = "0xa5f565650890fba1824ee0f21ebbbf660a179934"
        const val HERMES_DELEGATE = "0xd1c44e466b70002ac54fb727ed7cbe8f92c782f1"
        const val USDC_TOKEN = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        /** The wallet's Ethereum address as the perps backend spells it: what a deposit credits and a withdrawal pays. */
        val L1_ADDRESS = TestWallet.ETH_ADDRESS.lowercase()
        val USDC = testAsset(PerpsFundingAssetId.USDC_ETHEREUM, symbol = "USDC", name = "USD Coin", decimals = 6)

        val TRADING_PUBLIC_KEY = "0x" + "11".repeat(40)
        const val HERMES_NONCE = 3L
        const val HERMES_DEADLINE = 1_791_237_600L

        const val ACCOUNT_INDEX = 738_887L
        const val AGGREGATOR_ACCOUNT_INDEX = 281_474_976_710_648L
        const val FIRST_LIGHTER_NONCE = 41L
        const val EVM_NONCE = 7L
        const val TX_TYPE_TRANSFER = 12
        const val TX_TYPE_WITHDRAW = 13
        const val WITHDRAWAL_DELAY_SECONDS = 90L
        const val QUOTE_TTL_MS = 60_000L
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAVAILABLE = 503
        val TRANSFER_FEE_UNITS: BigInteger = BigInteger.valueOf(100_000L)
        val TRANSFER_MEMO = "0x" + "ab".repeat(32)

        private const val ONE_ETH = "1000000000000000000"
        private const val LIGHTER_CHAIN_ID = 304
        private const val PRIVATE_KEY_SIZE = 32
        private const val HASH_HEX_LENGTH = 80
        private const val ABI_WORD_LENGTH = 64
        private const val HEX_RADIX = 16
        private const val AMOUNT_OF_WITHDRAW = 3
        private const val NONCE_OF_WITHDRAW = 4
        private const val EXPIRY_OF_WITHDRAW = 5
        private const val NONCE_OF_TRANSFER = 2
        private const val EXPIRY_OF_TRANSFER = 3
        private val GATEWAY_USDC_ASSET_INDEX: BigInteger = BigInteger.valueOf(3)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

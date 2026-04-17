package com.tonapps.wallet.api

import android.content.Context
import android.net.Uri
import androidx.collection.ArrayMap
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.errors.SendBlockchainException
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.map
import com.tonapps.extensions.toUriOrNull
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.network.SSEvent
import com.tonapps.network.execute
import com.tonapps.network.get
import com.tonapps.network.post
import com.tonapps.network.postJSON
import com.tonapps.network.requestBuilder
import com.tonapps.network.sse
import com.tonapps.network.ssePost
import com.tonapps.wallet.api.Constants.SWAP_API
import com.tonapps.wallet.api.Constants.TRADING_API
import com.tonapps.wallet.api.configs.CountryConfig
import com.tonapps.wallet.api.core.ExchangeAPI
import com.tonapps.wallet.api.core.TradingAPI
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.api.entity.ChartEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.api.entity.toncenter.ToncenterSSERequest
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.api.extensions.toTokenEntity
import com.tonapps.wallet.api.internal.ConfigRepository
import com.tonapps.wallet.api.internal.InternalApi
import com.tonapps.wallet.api.internal.SwapApi
import com.tonapps.wallet.api.tron.TronApi
import io.Serializer
import io.batteryapi.apis.DefaultApi
import io.batteryapi.models.Balance
import io.batteryapi.models.Config
import io.batteryapi.models.EstimateGaslessCostRequest
import io.batteryapi.models.RechargeMethods
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import io.tonapi.models.Account
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import io.tonapi.models.AccountStatus
import io.tonapi.models.EmulateMessageToWalletRequest
import io.tonapi.models.EmulateMessageToWalletRequestParamsInner
import io.tonapi.models.GetWalletsByPublicKeyBulkRequest
import io.tonapi.models.MessageConsequences
import io.tonapi.models.NftItem
import io.tonapi.models.SendBlockchainMessageRequest
import io.tonapi.models.TokenRates
import io.tonapi.models.WalletsByPublicKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.crypto.hex
import org.ton.tlb.asRef
import java.math.BigDecimal
import java.util.Locale

@Suppress("LargeClass")
class API(
    private val context: Context,
    private val scope: CoroutineScope
) : CoreAPI(context) {

    private val internalApi = InternalApi(context, defaultHttpClient, appVersionName)
    private val swapApi = SwapApi(defaultHttpClient)
    private val configRepository = ConfigRepository(context, scope, internalApi)

    val configFlow: Flow<ConfigEntity>
        get() = configRepository.stream

    private val mainnetConfig: ConfigEntity
        get() = getConfig(TonNetwork.MAINNET)

    private val tonAPIHttpClient: OkHttpClient by lazy {
        tonAPIHttpClient { getConfig(TonNetwork.MAINNET) }
    }

    private val sseHttpClient: OkHttpClient by lazy {
        sseHttpClient { getConfig(TonNetwork.MAINNET) }
    }

    private val bridgeUrl: String
        get() = "${mainnetConfig.tonConnectBridgeHost}/bridge"

    val country: String
        get() = internalApi.country

    val exchange: ExchangeAPI by lazy {
//        ExchangeAPI("https://dev-swap.tonkeeper.com", tonAPIHttpClient)
        ExchangeAPI(SWAP_API, tonAPIHttpClient)
    }

    val trading: TradingAPI by lazy {
        TradingAPI(TRADING_API, tonAPIHttpClient)
    }

    //TODO TK-371
    private val _providerState: StateFlow<Provider> = configRepository.stream
        .map { buildProvider() }
        .stateIn(scope, SharingStarted.Eagerly, buildProvider())

    private val _batteryProviderState: StateFlow<BatteryProvider> = configRepository.stream
        .map { buildBatteryProvider() }
        .stateIn(scope, SharingStarted.Eagerly, buildBatteryProvider())

    private val provider: Provider
        get() = _providerState.value
    private val batteryProvider: BatteryProvider
        get() = _batteryProviderState.value

    val tron: TronApi by lazy {
        TronApi(mainnetConfig, defaultHttpClient, batteryProvider.default.get(TonNetwork.MAINNET))
    }

    private fun buildProvider() = Provider(
        mainnetHost = getConfig(TonNetwork.MAINNET).tonapiMainnetHost,
        testnetHost = getConfig(TonNetwork.TESTNET).tonapiTestnetHost,
        tetraHost = getConfig(TonNetwork.TETRA).tonapiMainnetHost,
        okHttpClient = tonAPIHttpClient
    )

    private fun buildBatteryProvider() = BatteryProvider(
        mainnetHost = getConfig(TonNetwork.MAINNET).batteryHost,
        testnetHost = getConfig(TonNetwork.TESTNET).batteryTestnetHost,
        tetraHost = getConfig(TonNetwork.TETRA).batteryHost,
        okHttpClient = tonAPIHttpClient
    )

    fun getConfig(network: TonNetwork) = configRepository.getConfig(network)

    fun setCountryConfig(config: CountryConfig) = internalApi.setConfig(config)

    suspend fun initConfig() = configRepository.initConfig()

    suspend fun tonapiFetch(
        url: String,
        options: String,
        network: TonNetwork
    ): Response = withContext(Dispatchers.IO) {
        val uri = url.toUriOrNull() ?: throw Exception("Invalid URL")
        if (uri.scheme != "https") {
            throw Exception("Invalid scheme. Should be https")
        }
        val host = uri.host ?: throw Exception("Invalid URL")
        if (host != "tonapi.io" && host.endsWith(".tonapi.io")) {
            throw Exception("Invalid host. Should be tonapi.io")
        }

        val builder = Request.Builder().url(url)

        val parsedOptions = JSONObject(options)
        val methodOptions = parsedOptions.optString("method") ?: "GET"
        val headersOptions = parsedOptions.optJSONObject("headers") ?: JSONObject()
        val bodyOptions = parsedOptions.optString("body") ?: ""
        var contentTypeOptions = "application/json"

        for (key in headersOptions.keys()) {
            val value = headersOptions.getString(key)
            if (key.equals("Authorization")) {
                builder.addHeader("X-Authorization", value)
            } else if (key.equals("Content-Type")) {
                contentTypeOptions = value
            } else {
                builder.addHeader(key, value)
            }
        }
        builder.addHeader("Authorization", "Bearer ${getConfig(network).tonApiV2Key}")

        if (methodOptions.equals("POST", ignoreCase = true)) {
            builder.post(bodyOptions.toRequestBody(contentTypeOptions.toMediaType()))
        }

        tonAPIHttpClient.newCall(builder.build()).execute()
    }

    fun accounts(network: TonNetwork) = provider.accounts.get(network)

    fun jettons(network: TonNetwork) = provider.jettons.get(network)

    fun wallet(network: TonNetwork) = provider.wallet.get(network)

    fun nft(network: TonNetwork) = provider.nft.get(network)

    fun blockchain(network: TonNetwork) = provider.blockchain.get(network)

    fun emulation(network: TonNetwork) = provider.emulation.get(network)

    fun liteServer(network: TonNetwork) = provider.liteServer.get(network)

    fun staking(network: TonNetwork) = provider.staking.get(network)

    fun events(network: TonNetwork) = provider.events.get(network)

    fun rates(network: TonNetwork) = provider.rates.get(network)

    fun battery(network: TonNetwork) = batteryProvider.default.get(network)

    fun batteryWallet(network: TonNetwork) = batteryProvider.wallet.get(network)

    fun batteryEmulation(network: TonNetwork) = batteryProvider.emulation.get(network)

    fun getBatteryConfig(network: TonNetwork): Config? {
        return withRetry { battery(network).getConfig() }
    }

    fun getBatteryRechargeMethods(network: TonNetwork): RechargeMethods? {
        return withRetry { battery(network).getRechargeMethods(false) }
    }

    fun getOnRampData() = internalApi.getOnRampData(mainnetConfig.webSwapsUrl)

    fun getOnRampPaymentMethods(currency: String) =
        internalApi.getOnRampPaymentMethods(mainnetConfig.webSwapsUrl, currency)

    fun getOnRampMerchants() = internalApi.getOnRampMerchants(mainnetConfig.webSwapsUrl)

    fun getSwapAssets(): JSONArray = runCatching {
        swapApi.getSwapAssets(mainnetConfig.webSwapsUrl)?.let(::JSONArray)
    }.getOrNull() ?: JSONArray()

    @Throws
    suspend fun calculateOnRamp(args: OnRampArgsEntity): OnRampMerchantEntity.Data = withContext(Dispatchers.IO) {
        val data = internalApi.calculateOnRamp(mainnetConfig.webSwapsUrl, args) ?: throw Exception("Empty response")
        val json = JSONObject(data)
        val items = json.getJSONArray("items").map { OnRampMerchantEntity(it) }
        val suggested = json.optJSONArray("suggested")?.map { OnRampMerchantEntity(it) } ?: emptyList()
        OnRampMerchantEntity.Data(
            items = items,
            suggested = suggested
        )
    }

    suspend fun getEthena(accountId: String): EthenaEntity? = withContext(Dispatchers.IO) {
        withRetry { internalApi.getEthena(accountId) }
    }

    fun getBatteryBalance(
        tonProofToken: String,
        network: TonNetwork,
        units: DefaultApi.UnitsGetBalance = DefaultApi.UnitsGetBalance.ton
    ): Balance? {
        return withRetry { battery(network).getBalance(tonProofToken, units, region = getConfig(network).region) }
    }

    fun getAlertNotifications() = withRetry {
        internalApi.getNotifications()
    } ?: emptyList()

    private fun isOkStatus(network: TonNetwork): Boolean {
        try {
            val status = withRetry {
                provider.utilities.get(network).status()
            } ?: return false
            if (!status.restOnline) {
                return false
            }
            if (status.indexingLatency > (5 * 60) - 30) {
                return false
            }
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    fun realtime(
        accountId: String,
        network: TonNetwork,
        config: ConfigEntity,
        onFailure: ((Throwable) -> Unit)?
    ): Flow<SSEvent> {
        val endpoint = when (network) {
            TonNetwork.TESTNET -> config.tonapiSSETestnetEndpoint
            TonNetwork.MAINNET -> config.tonapiSSEEndpoint
            TonNetwork.TETRA -> config.tonapiSSEEndpoint
        }
        val url = "$endpoint/sse/traces?account=$accountId&token=${config.tonApiV2Key}"
        return sseHttpClient.sse(url, onFailure = onFailure)
    }

    fun realtimeV2(
        accountId: String,
        network: TonNetwork,
        config: ConfigEntity,
        onFailure: ((Throwable) -> Unit)?
    ): Flow<SSEvent> {
        val endpoint = when (network) {
            TonNetwork.TESTNET -> config.toncenterSSETestnetEndpoint
            TonNetwork.MAINNET -> config.toncenterSSEEndpoint
            TonNetwork.TETRA -> config.toncenterSSEEndpoint
        }
        val request = ToncenterSSERequest(
            addresses = listOf(accountId),
            types = listOf("transactions"),
            minFinality = "pending",
        )
        val url = "${endpoint}/streaming/v2/sse"
        return sseHttpClient.ssePost(url, request.toJSON().toString(), onFailure = onFailure)
    }

    suspend fun refreshConfig() {
        configRepository.refresh()
    }

    fun swapStream(
        from: SwapAssetParam,
        to: SwapAssetParam,
        userAddress: String
    ) = swapApi.stream(mainnetConfig.webSwapsUrl, from, to, userAddress)

    suspend fun getPageTitle(url: String): String = withContext(Dispatchers.IO) {
        try {
            val headers = ArrayMap<String, String>().apply {
                set("Connection", "close")
            }
            val html = defaultHttpClient.get(url, headers)

            val ogTitle = Regex("""<meta\s+property=["']og:title["']\s+content=["'](.+?)["']""", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.get(1)

            if (!ogTitle.isNullOrBlank()) {
                return@withContext ogTitle
            }

            val metaTitle = Regex("""<meta\s+name=["']title["']\s+content=["'](.+?)["']""", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.get(1)

            if (!metaTitle.isNullOrBlank()) {
                return@withContext metaTitle
            }

            val title = Regex("""(?i)<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)
                ?.groupValues
                ?.get(1)

            title?.trim() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun get(url: String): String {
        val headers = ArrayMap<String, String>().apply {
            set("Connection", "close")
        }
        return defaultHttpClient.get(url, headers)
    }

    fun getBurnAddress() = mainnetConfig.burnZeroDomain.ifBlank {
        "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ"
    }

    suspend fun getDnsExpiring(
        accountId: String,
        network: TonNetwork,
        period: Int
    ) = withContext(Dispatchers.IO) {
        withRetry { accounts(network).getAccountDnsExpiring(accountId, period).items } ?: emptyList()
    }

    fun getEvents(
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null,
        limit: Int = 20
    ): AccountEvents? = withRetry {
        accounts(network).getAccountEvents(
            accountId = accountId,
            limit = limit,
            beforeLt = beforeLt,
            subjectOnly = true
        )
    }

    fun fetchTonEvents(
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null,
        beforeTimestamp: Timestamp? = null,
        afterTimestamp: Timestamp? = null,
        limit: Int,
    ): List<AccountEvent> {
        val response = withRetry {
            accounts(network).getAccountEvents(
                accountId = accountId,
                beforeLt = beforeLt,
                endDate = beforeTimestamp?.seconds(),
                startDate = afterTimestamp?.seconds(),
                limit = limit,
                subjectOnly = true,
            )
        } ?: throw Exception("Failed to get events")
        return response.events
    }

    suspend fun fetchTronTransactions(
        tronAddress: String,
        tonProofToken: String,
        beforeTimestamp: Timestamp? = null,
        afterTimestamp: Timestamp? = null,
        limit: Int
    ) = tron.getTronHistory(tronAddress, tonProofToken, limit, beforeTimestamp, afterTimestamp)

    suspend fun getTransactionByHash(
        accountId: String,
        network: TonNetwork,
        hash: String,
        attempt: Int = 0
    ): AccountEventEntity? {
        try {
            val body = accounts(network).getAccountEvent(accountId, hash)
            return AccountEventEntity(accountId, network.isTestnet, hash, body)
        } catch (e: Throwable) {
            if (attempt >= 10 || e is CancellationException) {
                return null
            } else if (e is ClientException && e.statusCode == 404) {
                delay(2000)
            } else {
                delay(1000)
            }
            return getTransactionByHash(accountId, network, hash, attempt + 1)
        }
    }

    suspend fun getSingleEvent(
        eventId: String,
        network: TonNetwork
    ): List<AccountEvent>? = withContext(Dispatchers.IO) {
        val event = withRetry { events(network).getEvent(eventId) } ?: return@withContext null
        val accountEvent = AccountEvent(
            eventId = eventId,
            account = AccountAddress(
                address = "",
                isScam = false,
                isWallet = false,
            ),
            timestamp = event.timestamp,
            actions = event.actions,
            isScam = event.isScam,
            lt = event.lt,
            inProgress = event.inProgress,
            extra = 0L,
            progress = 0f,
        )
        listOf(accountEvent)
    }

    fun getTokenEvents(
        tokenAddress: String,
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null,
        limit: Int = 10
    ): AccountEvents {
        return accounts(network).getAccountJettonHistoryByID(
            jettonId = tokenAddress,
            accountId = accountId,
            limit = limit,
            beforeLt = beforeLt
        )
    }

    fun getTonBalance(
        accountId: String,
        network: TonNetwork,
        currency: String,
    ): BalanceEntity? {
        val account = getAccount(accountId, network, currency) ?: return null
        val initializedAccount =
            account.status != AccountStatus.uninit && account.status != AccountStatus.nonexist
        return BalanceEntity(
            token = TokenEntity.TON,
            value = Coins.of(account.balance),
            walletAddress = accountId,
            initializedAccount = initializedAccount,
            isRequestMinting = false,
            isTransferable = true,
            lastActivity = account.lastActivity
        )
    }

    fun getJetton(
        accountId: String,
        network: TonNetwork
    ): TokenEntity? {
        val jettonsAPI = jettons(network)
        val jetton = withRetry {
            jettonsAPI.getJettonInfo(accountId)
        } ?: return null
        return jetton.toTokenEntity()
    }

    fun getJettonCustomPayload(
        accountId: String,
        network: TonNetwork,
        jettonId: String
    ): TokenEntity.TransferPayload? {
        val jettonsAPI = jettons(network)
        val payload = withRetry {
            jettonsAPI.getJettonTransferPayload(accountId, jettonId)
        } ?: return null
        return TokenEntity.TransferPayload(
            tokenAddress = jettonId,
            customPayload = payload.customPayload?.cellFromHex(),
            stateInit = payload.stateInit?.cellFromHex()?.asRef(StateInit),
        )
    }

    fun getJettonsBalances(
        accountId: String,
        network: TonNetwork,
        currency: String? = null,
        extensions: List<String>? = null
    ): List<BalanceEntity>? {
        val jettonsBalances = withRetry {
            accounts(network).getAccountJettonsBalances(
                accountId = accountId,
                currencies = currency?.let { listOf(it) },
                supportedExtensions = extensions,
            ).balances
        } ?: return null
        return jettonsBalances.map { jb ->
            BalanceEntity(
                token = jb.jetton.toTokenEntity(jb.extensions, jb.lock),
                value = Coins.of(
                    BigDecimal(jb.balance).movePointLeft(jb.jetton.decimals),
                    jb.jetton.decimals
                ),
                walletAddress = jb.walletAddress.address,
                initializedAccount = true,
                isRequestMinting = jb.extensions?.contains(TokenEntity.Extension.CustomPayload.value) == true,
                isTransferable = jb.extensions?.contains(TokenEntity.Extension.NonTransferable.value) != true,
                numerator = jb.jetton.scaledUi?.numerator?.toBigDecimal(),
                denominator = jb.jetton.scaledUi?.denominator?.toBigDecimal()
            ).also { it.rates = jb.price }
        }.filter { it.value.isPositive }
    }

    fun resolveAddressOrName(
        query: String,
        network: TonNetwork
    ): AccountDetailsEntity? {
        return try {
            val account = getAccount(query, network, null) ?: return null
            val details = AccountDetailsEntity(query, account, network)
            if (details.walletVersion != WalletVersion.UNKNOWN) {
                details
            } else {
                details.copy(
                    walletVersion = getWalletVersionByAddress(account.address, network)
                )
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun getWalletVersionByAddress(address: String, network: TonNetwork): WalletVersion {
        val pk = getPublicKey(address, network) ?: return WalletVersion.UNKNOWN
        return BaseWalletContract.resolveVersion(pk, address.toRawAddress(), network)
    }

    fun resolvePublicKeysBulk(
        publicKeys: List<PublicKeyEd25519>,
        network: TonNetwork,
        installId: String?
    ): List<WalletsByPublicKey>? {
        val request = GetWalletsByPublicKeyBulkRequest(publicKeys = publicKeys.map { it.hex() })
        return withRetry {
            wallet(network).getWalletsByPublicKeyBulk(
                F = installId,
                getWalletsByPublicKeyBulkRequest = request
            ).items
        }
    }

    fun resolvePublicKey(
        pk: PublicKeyEd25519,
        network: TonNetwork
    ): List<AccountDetailsEntity> {
        return try {
            val query = pk.hex()
            val wallets = withRetry {
                wallet(network).getWalletsByPublicKey(query).accounts
            } ?: return emptyList()
            wallets.map { AccountDetailsEntity(
                query = query,
                wallet = it,
                network = network
            ) }.map {
                if (it.walletVersion == WalletVersion.UNKNOWN) {
                    it.copy(
                        walletVersion = BaseWalletContract.resolveVersion(
                            pk,
                            it.address.toRawAddress(),
                            network
                        )
                    )
                } else {
                    it
                }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun getRates(network: TonNetwork, currency: String, tokens: List<String>): Map<String, TokenRates>? {
        val currencies = listOf(currency, "TON")
        return withRetry {
            rates(network).getRates(
                tokens = tokens,
                currencies = currencies
            ).rates
        }
    }

    fun getRates(network: TonNetwork, from: String, to: String): Map<String, TokenRates>? {
        return withRetry {
            rates(network).getRates(
                tokens = listOf(from),
                currencies = listOf(to)
            ).rates
        }
    }

    fun getNft(address: String, network: TonNetwork): NftItem? {
        return withRetry { nft(network).getNftItemByAddress(address) }
    }

    fun getNftItems(
        address: String,
        network: TonNetwork,
        limit: Int = 1000
    ): List<NftItem>? {
        return withRetry {
            accounts(network).getAccountNftItems(
                accountId = address,
                limit = limit,
                indirectOwnership = true,
            ).nftItems
        }
    }

    private fun getPublicKey(
        accountId: String,
        network: TonNetwork
    ): PublicKeyEd25519? {
        val hex = withRetry {
            accounts(network).getAccountPublicKey(accountId)
        }?.publicKey ?: return null
        return PublicKeyEd25519(hex(hex))
    }

    fun safeGetPublicKey(
        accountId: String,
        network: TonNetwork
    ) = getPublicKey(accountId, network) ?: EmptyPrivateKeyEd25519.publicKey()

    fun tonconnectEvents(
        publicKeys: List<String>,
        lastEventId: Long? = null,
        onFailure: ((Throwable) -> Unit)?
    ): Flow<SSEvent> {
        if (publicKeys.isEmpty()) {
            return emptyFlow()
        }
        val value = publicKeys.joinToString(",")
        val url = "${bridgeUrl}/events?client_id=$value"
        return sseHttpClient.sse(url, lastEventId, onFailure).filter { it.type == "message" }
    }

    fun tonconnectPayload(): String? {
        try {
            val url = "${mainnetConfig.tonapiMainnetHost}/v2/tonconnect/payload"
            val json = withRetry {
                JSONObject(tonAPIHttpClient.get(url))
            } ?: return null
            return json.getString("payload")
        } catch (e: Throwable) {
            return null
        }
    }

    suspend fun batteryVerifyPurchasePromo(network: TonNetwork, code: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                battery(network).verifyPurchasePromo(code)
                true
            } catch (e: Throwable) {
                false
            }
        }

    fun tonconnectProof(address: String, proof: String): String {
        val url = "${mainnetConfig.tonapiMainnetHost}/v2/wallet/auth/proof"
        val data = "{\"address\":\"$address\",\"proof\":$proof}"
        val response = withRetry {
            tonAPIHttpClient.postJSON(url, data)
        } ?: throw Exception("Empty response")
        if (!response.isSuccessful) {
            throw Exception("Failed creating proof: ${response.code}")
        }
        val body = response.body?.string() ?: throw Exception("Empty response")
        return JSONObject(body).getString("token")
    }

    fun tonconnectSend(
        publicKeyHex: String,
        clientId: String,
        body: String
    ) {
        val mimeType = "text/plain".toMediaType()
        val url = "${bridgeUrl}/message?client_id=$publicKeyHex&to=$clientId&ttl=300"
        withRetry {
            tonAPIHttpClient.post(url, body.toRequestBody(mimeType))
        }
    }

    fun estimateGaslessCost(
        tonProofToken: String,
        jettonMaster: String,
        cell: Cell,
        network: TonNetwork,
    ): String? {
        val request = EstimateGaslessCostRequest(cell.base64(), false)

        return withRetry {
            battery(network).estimateGaslessCost(jettonMaster, request, tonProofToken).commission
        }
    }

    fun emulateWithBattery(
        tonProofToken: String,
        cell: Cell,
        network: TonNetwork,
        safeModeEnabled: Boolean,
    ) = emulateWithBattery(tonProofToken, cell.base64(), network, safeModeEnabled)

    fun emulateWithBattery(
        tonProofToken: String,
        boc: String,
        network: TonNetwork,
        safeModeEnabled: Boolean,
    ): EmulateWithBatteryResult? {
        val host = when (network) {
            TonNetwork.TESTNET -> mainnetConfig.batteryTestnetHost
            TonNetwork.MAINNET -> mainnetConfig.batteryHost
            TonNetwork.TETRA -> mainnetConfig.batteryHost
        }
        val url = "$host/wallet/emulate"
        val data = "{\"boc\":\"$boc\",\"safe_mode\":$safeModeEnabled}"

        val response = withRetry {
            tonAPIHttpClient.postJSON(url, data, ArrayMap<String, String>().apply {
                set("X-TonConnect-Auth", tonProofToken)
            })
        } ?: return null

        val supportedByBattery = response.headers["supported-by-battery"] == "true"
        val allowedByBattery = response.headers["allowed-by-battery"] == "true"
        val excess = response.headers["excess"]?.toLongOrNull()
        val withBattery = supportedByBattery && allowedByBattery

        val string = response.body.use { it.string() }
        if (string.isBlank()) {
            L.e("emulateWithBattery", "Empty response body, code=${response.code}")
            return null
        }
        val consequences = try {
            Serializer.JSON.decodeFromString<MessageConsequences>(string)
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
        return EmulateWithBatteryResult(consequences, withBattery, excess)
    }

    suspend fun emulate(
        boc: String,
        network: TonNetwork,
        address: String? = null,
        balance: Long? = null,
        safeModeEnabled: Boolean,
    ): MessageConsequences? = withContext(Dispatchers.IO) {
        val params = mutableListOf<EmulateMessageToWalletRequestParamsInner>()
        if (address != null) {
            params.add(EmulateMessageToWalletRequestParamsInner(address, balance))
        }
        val request = EmulateMessageToWalletRequest(
            boc = boc,
            params = params,
            // safeMode = safeModeEnabled
        )
        withRetry {
            emulation(network).emulateMessageToWallet(request)
        }
    }

    suspend fun emulate(
        cell: Cell,
        network: TonNetwork,
        address: String? = null,
        balance: Long? = null,
        safeModeEnabled: Boolean,
    ): MessageConsequences? {
        return emulate(cell.hex(), network, address, balance, safeModeEnabled)
    }

    suspend fun sendToBlockchainWithBattery(
        boc: String,
        tonProofToken: String,
        network: TonNetwork,
        source: String,
        confirmationTime: Double,
    ) = withContext(Dispatchers.IO) {
        if (!isOkStatus(network)) {
            throw SendBlockchainException.SendBlockchainStatusException
        }

        val request = io.batteryapi.models.EmulateMessageToWalletRequest(
            boc = boc,
        )

        try {
            battery(network).sendMessage(tonProofToken, request)
        } catch (e: Throwable) {
            throwSendBlockchainError(e)
        }
    }

    suspend fun sendToBlockchain(
        boc: String,
        network: TonNetwork,
        source: String,
        confirmationTime: Double,
    ) = withContext(Dispatchers.IO) {
        if (!isOkStatus(network)) {
            throw SendBlockchainException.SendBlockchainStatusException
        }

        val meta = hashMapOf(
            "platform" to "android",
            "version" to appVersionName,
            "source" to source,
            "confirmation_time" to confirmationTime.toString()
        )
        val request = SendBlockchainMessageRequest(
            boc = boc,
            meta = meta
        )
        try {
            blockchain(network).sendBlockchainMessage(request)
        } catch (e: Throwable) {
            throwSendBlockchainError(e)
        }
    }

    fun getAccountSeqno(
        accountId: String,
        network: TonNetwork,
    ): Int = withRetry { wallet(network).getAccountSeqno(accountId).seqno } ?: 0

    suspend fun resolveAccount(
        value: String,
        network: TonNetwork,
    ): Account? = withContext(Dispatchers.IO) {
        /*if (value.isValidTonAddress()) {
            return@withContext getAccount(value, network)
        }
        return@withContext resolveDomain(value.lowercase().trim(), network)*/
        getAccount(value, network, null)
    }

    /*private suspend fun resolveDomain(domain: String, testnet: Boolean): Account? {
        return getAccount(domain, testnet) ?: getAccount(domain.unicodeToPunycode(), testnet)
    }*/

    private fun getAccount(
        accountId: String,
        network: TonNetwork,
        currency: String?,
    ): Account? {
        var normalizedAccountId = accountId
        if (normalizedAccountId.startsWith("https://")) {
            normalizedAccountId = normalizedAccountId.replace("https://", "")
        }
        if (normalizedAccountId.startsWith("t.me/")) {
            normalizedAccountId = normalizedAccountId.replace("t.me/", "")
            normalizedAccountId = "$normalizedAccountId.t.me"
        }
        if (!normalizedAccountId.isValidTonAddress()) {
            normalizedAccountId = normalizedAccountId.lowercase().trim()
        }
        return withRetry { accounts(network).getAccount(normalizedAccountId) }
    }

    fun pushSubscribe(
        locale: Locale,
        firebaseToken: String,
        deviceId: String,
        accounts: List<String>
    ): Boolean {
        if (accounts.isEmpty()) {
            return true
        }
        val url = "${mainnetConfig.tonapiMainnetHost}/v1/internal/pushes/plain/subscribe"

        val accountsArray = JSONArray()
        for (account in accounts) {
            val jsonAccount = JSONObject()
            jsonAccount.put("address", account)
            accountsArray.put(jsonAccount)
        }

        val json = JSONObject()
        json.put("locale", locale.toString())
        json.put("device", deviceId)
        json.put("token", firebaseToken)
        json.put("accounts", accountsArray)

        return withRetry {
            val response = tonAPIHttpClient.postJSON(url, json.toString())
            response.isSuccessful
        } ?: false
    }

    fun pushUnsubscribe(
        deviceId: String,
        accounts: List<String>
    ): Boolean {
        if (accounts.isEmpty()) {
            return true
        }

        val url = "${mainnetConfig.tonapiMainnetHost}/v1/internal/pushes/plain/unsubscribe"

        val accountsArray = JSONArray()
        for (account in accounts) {
            val jsonAccount = JSONObject()
            jsonAccount.put("address", account)
            accountsArray.put(jsonAccount)
        }

        val json = JSONObject()
        json.put("device", deviceId)
        json.put("accounts", accountsArray)

        return withRetry {
            val response = tonAPIHttpClient.postJSON(url, json.toString())
            response.isSuccessful
        } ?: false
    }

    fun getStories(id: String) = internalApi.getStories(id)

    fun pushTonconnectSubscribe(
        token: String,
        appUrl: String,
        accountId: String,
        firebaseToken: String,
        sessionId: String?,
        commercial: Boolean,
        silent: Boolean
    ): Boolean {
        val url = "${mainnetConfig.tonapiMainnetHost}/v1/internal/pushes/tonconnect"

        val json = JSONObject()
        json.put("app_url", appUrl)
        json.put("account", accountId)
        json.put("firebase_token", firebaseToken)
        sessionId?.let { json.put("session_id", it) }
        json.put("commercial", commercial)
        json.put("silent", !silent)
        val data = json.toString().replace("\\/", "/").trim()

        val headers = ArrayMap<String, String>().apply {
            set("X-TonConnect-Auth", token)
            set("Connection", "close")
        }

        val response = withRetry {
            tonAPIHttpClient.postJSON(url, data, headers)
        }

        return response?.isSuccessful ?: false
    }

    fun pushTonconnectUnsubscribe(
        token: String,
        appUrl: String,
        accountId: String,
        firebaseToken: String,
    ): Boolean {
        return try {
            val uriBuilder =
                Uri.parse("${mainnetConfig.tonapiMainnetHost}/v1/internal/pushes/tonconnect").buildUpon()
            uriBuilder.appendQueryParameter("firebase_token", firebaseToken)
            uriBuilder.appendQueryParameter("app_url", appUrl)
            uriBuilder.appendQueryParameter("account", accountId)

            val builder = requestBuilder(uriBuilder.build().toString())
            builder.delete()
            builder.addHeader("X-TonConnect-Auth", token)
            builder.addHeader("Connection", "close")
            tonAPIHttpClient.execute(builder.build()).isSuccessful
        } catch (e: Throwable) {
            false
        }
    }

    fun getPushFromApps(
        token: String,
        accountId: String,
    ): JSONArray {
        return try {
            val url = "${mainnetConfig.tonapiMainnetHost}/v1/messages/history?account=$accountId"
            val response = withRetry {
                tonAPIHttpClient.get(url, ArrayMap<String, String>().apply {
                    set("X-TonConnect-Auth", token)
                })
            } ?: throw Exception("Empty response")
            val json = JSONObject(response)
            json.getJSONArray("items")
        } catch (e: Throwable) {
            JSONArray()
        }
    }

    fun getBrowserApps(network: TonNetwork, locale: Locale): JSONObject {
        return internalApi.getBrowserApps(network, locale)
    }

    fun getCurrencies(network: TonNetwork, locale: Locale): JSONArray {
        return internalApi.getCurrencies(network, locale)
    }

    fun getFiatMethods(network: TonNetwork, locale: Locale): JSONObject? {
        return withRetry { internalApi.getFiatMethods(network, locale) }
    }

    fun getTransactionEvents(accountId: String, network: TonNetwork, eventId: String): AccountEvent? {
        return try {
            accounts(network).getAccountEvent(accountId, eventId)
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getScamDomains(): Array<String> = withContext(Dispatchers.IO) {
        internalApi.getScamDomains()
    }

    fun loadChart(
        token: String,
        currency: String,
        startDate: Long,
        endDate: Long
    ): List<ChartEntity> {
        try {
            val url =
                "${mainnetConfig.tonapiMainnetHost}/v2/rates/chart?token=$token&currency=$currency&start_date=$startDate&end_date=$endDate"
            val array = JSONObject(tonAPIHttpClient.get(url)).getJSONArray("points")
            return (0 until array.length()).map { index ->
                ChartEntity(array.getJSONArray(index))
            }.asReversed()
        } catch (e: Throwable) {
            return listOf(ChartEntity(0, 0f))
        }
    }

    fun getServerTime(network: TonNetwork): Int {
        /*val time = serverTimeProvider.getServerTime(network)
        if (time == null) {
            val serverTimeSeconds = withRetry { liteServer(network).getRawTime().time }
            if (serverTimeSeconds == null) {
                return (System.currentTimeMillis() / 1000).toInt()
            }
            serverTimeProvider.setServerTime(network, serverTimeSeconds)
            return serverTimeSeconds
        }
        return time*/
        val serverTimeSeconds = withRetry { liteServer(network).getRawTime().time }
        if (serverTimeSeconds == null) {
            return (System.currentTimeMillis() / 1000).toInt()
        }
        return serverTimeSeconds
    }

    suspend fun resolveCountry(): String? = internalApi.resolveCountry()

    suspend fun reportNtfSpam(
        nftAddress: String,
        scam: Boolean
    ) = withContext(Dispatchers.IO) {
        val url = mainnetConfig.scamEndpoint + "/v1/report/$nftAddress"
        val data = "{\"is_scam\":$scam}"
        val response = withRetry {
            tonAPIHttpClient.postJSON(url, data)
        } ?: throw Exception("Empty response")
        if (!response.isSuccessful) {
            throw Exception("Failed creating proof: ${response.code}")
        }
        response.body?.string() ?: throw Exception("Empty response")
    }

    suspend fun reportTX(
        txId: String,
        comment: String?,
        recipient: String,
    ) = withContext(Dispatchers.IO) {
        val url = mainnetConfig.scamEndpoint + "/v1/report/tx/$txId"
        val json = JSONObject()
        json.put("recipient", recipient)
        comment?.let { json.put("comment", it) }
        val data = json.toString()
        val response = withRetry {
            tonAPIHttpClient.postJSON(url, data)
        } ?: throw Exception("Empty response")
        if (!response.isSuccessful) {
            throw Exception("Failed creating proof: ${response.code}")
        }
        response.body?.string() ?: throw Exception("Empty response")
    }

    private fun throwSendBlockchainError(e: Throwable): Nothing {
        val errorText = when (e) {
            is ClientException -> (e.response as? ClientError<*>)?.body?.parseJsonError()
            is ServerException -> (e.response as? ServerError<*>)?.body?.parseJsonError()
            else -> null
        }
        throw SendBlockchainException.SendBlockchainErrorException(e, errorText)
    }

    private fun Any.parseJsonError(): String? = try {
        JSONObject(toString()).getString("error")
    } catch (e: Exception) {
        null
    }
}

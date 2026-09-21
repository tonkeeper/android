package com.tonapps.wallet.api.tron

import android.net.Uri
import androidx.collection.arrayMapOf
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.blockchain.tron.encodeTronAddress
import com.tonapps.blockchain.tron.toEvmHex
import com.tonapps.blockchain.tron.tronHex
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.extensions.fromHex
import com.tonapps.extensions.map
import com.tonapps.icu.Coins
import com.tonapps.network.get
import com.tonapps.network.postJSON
import com.tonapps.network.backoff.ExponentialBackoff
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.api.readBody
import com.tonapps.wallet.api.tron.entity.TronEstimationEntity
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.api.tron.entity.TronResourcePrices
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.api.withRetry
import io.batteryapi.apis.DefaultApi
import io.batteryapi.models.EstimatedTronTx
import io.batteryapi.models.EstimatedTronTxInstantFeeAcceptedAssetsInner
import io.batteryapi.models.TronSendRequest
import io.ktor.util.encodeBase64
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.ton.cell.Cell
import java.math.BigInteger

class TronApi(
    private val config: ConfigEntity,
    private val okHttpClient: OkHttpClient,
    private val batteryApi: DefaultApi
) {

    sealed interface Keys : CacheKey {
        object TronPrices : Keys {
            override val ttl: Long = CacheKey.DEFAULT_TTL
        }
    }

    val transferDefaultResources = TronResourcesEntity(
        energy = 64285,
        bandwidth = 345,
    )

    private var safetyMargin: Double? = null
    private val resourcePricesCache = TimedCacheMemory<Keys>()

    private val tronApiKey: String?
        get() = config.tronApiKey?.ifBlank { null }

    private fun headers() = arrayMapOf<String, String>().apply {
        tronApiKey?.let {
            put("TRON-PRO-API-KEY", it)
        }
    }

    private fun post(
        uri: Uri,
        body: JsonObject
    ) = tronRetry {
        okHttpClient.postJSON(uri.toString(), body.toString(), headers())
    } ?: throw Exception("tron api failed")

    private fun get(uri: Uri) = tronRetry {
        okHttpClient.get(uri.toString(), headers())
    } ?: throw Exception("tron api failed")

    fun getTronUsdtBalance(
        tronAddress: String,
    ): BalanceEntity {
        return getTronUsdtBalanceOrNull(tronAddress) ?: BalanceEntity(
            token = TokenEntity.TRON_USDT,
            value = Coins.of(BigInteger.ZERO, TokenEntity.TRON_USDT.decimals),
            walletAddress = tronAddress,
        )
    }

    fun getTronUsdtBalanceOrNull(
        tronAddress: String,
    ): BalanceEntity? {
        try {
            val builder = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("wallet/triggersmartcontract")
            val url = builder.build()

            val requestBody = buildJsonObject {
                put("owner_address", tronAddress.tronHex())
                put("contract_address", TokenEntity.TRON_USDT.address.tronHex())
                put("function_selector", "balanceOf(address)")
                put("parameter", tronAddress.encodeTronAddress())
            }

            val response = post(url, requestBody)
            val body = response.readBody()
            val json = JSONObject(body)

            val constantResultArray = json.optJSONArray("constant_result")
            val hexBalance = constantResultArray?.optString(0)
            val balance = hexBalance?.toBigInteger(16) ?: BigInteger.ZERO

            return BalanceEntity(
                token = TokenEntity.TRON_USDT,
                value = Coins.of(balance.toLong(), TokenEntity.TRON_USDT.decimals),
                walletAddress = tronAddress,
            )
        } catch (_: Throwable) {
            return null
        }
    }

    fun getTrxBalance(
        tronAddress: String,
    ): BalanceEntity {
        return getTronAccountBalances(tronAddress).trx
    }

    fun getTrxBalanceOrNull(
        tronAddress: String,
    ): BalanceEntity? {
        return getTronAccountBalancesOrNull(tronAddress)?.trx
    }

    /**
     * Single TronGrid request for TRX + USDT TRC20 balances via `/v1/accounts/{address}`.
     */
    fun getTronAccountBalances(tronAddress: String): TronAccountBalances {
        return getTronAccountBalancesOrNull(tronAddress)
            ?: emptyTronAccountBalances(tronAddress)
    }

    fun getTronAccountBalancesOrNull(tronAddress: String): TronAccountBalances? {
        return try {
            val builder = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("v1/accounts/$tronAddress")
            val account = JSONObject(get(builder.build()))
                .getJSONArray("data")
                .optJSONObject(0)

            val balanceSun = account?.optLong("balance") ?: 0L
            val usdtNano = parseTrc20Balance(account, TokenEntity.TRC20_USDT)

            tronAccountBalances(
                tronAddress = tronAddress,
                trxSun = BigInteger.valueOf(balanceSun),
                usdtNano = usdtNano,
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Batch TRX + USDT balances via TronGrid `POST /jsonrpc` (eth_getBalance + eth_call). */
    fun getTronAccountBalancesBatch(addresses: List<String>): Map<String, TronAccountBalances> {
        if (addresses.isEmpty()) {
            return emptyMap()
        }
        return buildMap {
            addresses.chunked(JSON_RPC_BALANCE_CHUNK).forEach { chunk ->
                putAll(fetchTronAccountBalancesJsonRpc(chunk))
            }
        }
    }

    private fun fetchTronAccountBalancesJsonRpc(
        addresses: List<String>,
    ): Map<String, TronAccountBalances> {
        return try {
            val usdtContract = "0x${TokenEntity.TRC20_USDT.toEvmHex()}"
            val requests = JSONArray()
            addresses.forEachIndexed { index, address ->
                val hexAddress = "0x${address.tronHex()}"
                requests.put(
                    jsonRpcRequest(
                        id = index * 2,
                        method = "eth_getBalance",
                        params = JSONArray().put(hexAddress).put("latest"),
                    ),
                )
                requests.put(
                    jsonRpcRequest(
                        id = index * 2 + 1,
                        method = "eth_call",
                        params = JSONArray()
                            .put(
                                JSONObject()
                                    .put("to", usdtContract)
                                    .put("data", "0x70a08231${address.encodeTronAddress()}"),
                            )
                            .put("latest"),
                    ),
                )
            }

            val url = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("jsonrpc")
                .build()
            val body = tronRetry {
                okHttpClient.postJSON(url.toString(), requests.toString(), headers()).readBody()
            } ?: throw Exception("tron jsonrpc failed")

            val byId = HashMap<Int, String>(addresses.size * 2)
            val responses = JSONArray(body)
            for (i in 0 until responses.length()) {
                val item = responses.optJSONObject(i) ?: continue
                val id = item.optInt("id", -1)
                if (id < 0 || item.has("error")) {
                    continue
                }
                val result = item.optString("result")
                if (result.isNullOrBlank() || result == "null") {
                    continue
                }
                byId[id] = result
            }

            addresses.mapIndexed { index, address ->
                address to tronAccountBalances(
                    tronAddress = address,
                    trxSun = parseHexQuantity(byId[index * 2]),
                    usdtNano = parseHexQuantity(byId[index * 2 + 1]),
                )
            }.toMap()
        } catch (_: Throwable) {
            addresses.associateWith(::emptyTronAccountBalances)
        }
    }

    private fun jsonRpcRequest(
        id: Int,
        method: String,
        params: JSONArray,
    ) = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", method)
        .put("params", params)

    private fun parseHexQuantity(hex: String?): BigInteger {
        if (hex.isNullOrBlank()) {
            return BigInteger.ZERO
        }
        val normalized = hex.removePrefix("0x").ifBlank { "0" }
        return normalized.toBigIntegerOrNull(16) ?: BigInteger.ZERO
    }

    private fun tronAccountBalances(
        tronAddress: String,
        trxSun: BigInteger,
        usdtNano: BigInteger,
    ) = TronAccountBalances(
        trx = BalanceEntity(
            token = TokenEntity.TRX,
            value = Coins.ofNano(trxSun.toString(), TokenEntity.TRX.decimals),
            walletAddress = tronAddress,
        ),
        usdt = BalanceEntity(
            token = TokenEntity.TRON_USDT,
            value = Coins.ofNano(usdtNano.toString(), TokenEntity.TRON_USDT.decimals),
            walletAddress = tronAddress,
        ),
    )

    private fun emptyTronAccountBalances(tronAddress: String) = tronAccountBalances(
        tronAddress = tronAddress,
        trxSun = BigInteger.ZERO,
        usdtNano = BigInteger.ZERO,
    )

    private fun parseTrc20Balance(account: JSONObject?, contractAddress: String): BigInteger {
        val trc20 = account?.optJSONArray("trc20") ?: return BigInteger.ZERO
        for (i in 0 until trc20.length()) {
            val item = trc20.optJSONObject(i) ?: continue
            val keys = item.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.equals(contractAddress, ignoreCase = true)) {
                    return item.optString(key).toBigIntegerOrNull() ?: BigInteger.ZERO
                }
            }
        }
        return BigInteger.ZERO
    }

    data class TronAccountBalances(
        val trx: BalanceEntity,
        val usdt: BalanceEntity,
    )

    private fun getTronBlockchainHistory(
        tronAddress: String,
        limit: Int,
        beforeTimestamp: Timestamp?,
        afterTimestamp: Timestamp?,
    ): List<TronEventEntity> {
        val builder = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("v1/accounts/$tronAddress/transactions/trc20")
            .appendQueryParameter("limit", limit.toString())
        beforeTimestamp?.toLong()?.let {
            builder.appendQueryParameter("max_timestamp", (it - 1).toString())
        }
        afterTimestamp?.toLong()?.let {
            builder.appendQueryParameter("min_timestamp", it.toString())
        }

        val body = get(builder.build())
        val json = JSONObject(body).getJSONArray("data")
        return json.map {
            if (it.getString("type") == "Transfer" && it.getJSONObject("token_info")
                    .getString("address") == TokenEntity.TRC20_USDT
            ) {
                TronEventEntity(it)
            } else {
                null
            }
        }.filterNotNull()
    }

    private fun getBatteryTransfersHistory(
        auth: Authorization,
        limit: Int,
        beforeTimestamp: Timestamp?,
    ): List<TronEventEntity> {
        if (auth.isEmpty) {
            return emptyList()
        }
        val maxTimestamp = beforeTimestamp?.toLong()?.let { it - 1 }
        val response = withRetry {
            batteryApi.getTronTransactions(
                tonConnectAuth = auth.tonProof,
                xWalletId = auth.walletId,
                limit = limit,
                maxTimestamp = maxTimestamp,
            )
        } ?: return emptyList()

        return response.transactions.filter { it.txid.isNotEmpty() }.map { TronEventEntity(it) }
    }

    suspend fun getTronHistory(
        tronAddress: String,
        auth: Authorization,
        limit: Int,
        beforeTimestamp: Timestamp?,
        afterTimestamp: Timestamp? = null,
    ): List<TronEventEntity> {
        val (blockchainEvents, batteryEvents) = coroutineScope {
            val blockchainEventsDeferred = async {
                getTronBlockchainHistory(
                    tronAddress,
                    limit,
                    beforeTimestamp,
                    afterTimestamp
                )
            }
            val batteryEventsDeferred =
                async { getBatteryTransfersHistory(auth, limit, beforeTimestamp) }
            Pair(blockchainEventsDeferred.await(), batteryEventsDeferred.await())
        }

        return (batteryEvents + blockchainEvents)
            .distinctBy { it.transactionHash }
            .sortedByDescending { it.timestamp }
    }

    private fun estimateBandwidth(rawHex: String): Int {
        return rawHex.fromHex().size +
                DATA_HEX_PROTOBUF_EXTRA + MAX_RESULT_SIZE_IN_TX + A_SIGNATURE
    }

    private fun estimateResources(transfer: TronTransfer): TronResourcesEntity {
        val builder =
            config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("wallet/triggerconstantcontract")
        val url = builder.build()

        val requestBody = buildJsonObject {
            put("owner_address", transfer.from)
            put("contract_address", transfer.contractAddress)
            put("function_selector", transfer.function)
            put("parameter", transfer.data)
            put("visible", true)
        }

        val response = post(url, requestBody)
        val body = response.readBody()
        val json = JSONObject(body)

        val resultObj = json.optJSONObject("result")
        if (resultObj?.optBoolean("result") != true) {
            throw Exception("Estimating energy error (invalid result field)")
        }

        val energy = json.optInt("energy_used", -1)
        if (energy < 0) {
            throw Exception("Estimating energy error (missing or invalid energy_used)")
        }

        val transaction = json.optJSONObject("transaction")
        val rawHex = transaction?.optString("raw_data_hex")
            ?: throw Exception("Transaction data missing in response")

        val bandwidth = estimateBandwidth(rawHex)

        return TronResourcesEntity(energy = energy, bandwidth = bandwidth)
    }

    private suspend fun getResourcePrices(): TronResourcePrices {
        return resourcePricesCache.getOrLoad(Keys.TronPrices) {
            val builder = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("wallet/getchainparameters")
            val url = builder.build()

            val requestBody = buildJsonObject { }

            val response = post(url, requestBody)
            val body = response.readBody()
            val json = JSONObject(body)

            val paramsArray = json.optJSONArray("chainParameter")
                ?: throw Exception("Missing chainParameter array in response")

            fun getValue(key: String): Long? {
                for (i in 0 until paramsArray.length()) {
                    val obj = paramsArray.optJSONObject(i) ?: continue
                    if (obj.optString("key") == key) {
                        val value = obj.opt("value")
                        if (value is Number) {
                            return value.toLong()
                        }
                    }
                }
                return null
            }

            val energySun = getValue("getEnergyFee")
            val bandwidthSun = getValue("getTransactionFee")

            if (energySun == null || bandwidthSun == null) {
                throw Exception("Missing or invalid energy or bandwidth price in chain parameters")
            }

            TronResourcePrices(
                energy = Coins.of(energySun, TokenEntity.TRX.decimals),
                bandwidth = Coins.of(bandwidthSun, TokenEntity.TRX.decimals)
            )
        }
    }

    suspend fun getBurnTrxAmountForResources(resources: TronResourcesEntity): Coins {
        val prices = getResourcePrices()
        val burnTrxForEnergy = prices.energy.value * resources.energy.toBigDecimal()
        val burnTrxForBandwidth = prices.bandwidth.value * resources.bandwidth.toBigDecimal()

        return Coins.of(
            burnTrxForEnergy + burnTrxForBandwidth,
            TokenEntity.TRX.decimals
        )
    }

    private fun applyResourcesSafetyMargin(resources: TronResourcesEntity): TronResourcesEntity {
        val margin = safetyMargin ?: run {
            val tronConfig = batteryApi.getTronConfig()
            val marginPercent = tronConfig.safetyMarginPercent.toIntOrNull() ?: 3
            val calculated = marginPercent / 100.0
            safetyMargin = calculated
            calculated
        }

        val energy = kotlin.math.ceil(resources.energy * (1 + margin)).toInt()
        val bandwidth = kotlin.math.ceil(resources.bandwidth * (1 + margin)).toInt()

        return resources.copy(energy = energy, bandwidth = bandwidth)
    }

    fun getAccountFreeBandwidth(tronAddress: String): Int = getAccountBandwidth(tronAddress)

    private fun getAccountBandwidth(tronAddress: String): Int {
        val url = config.tronApiUrl.toUri()
            .buildUpon()
            .appendEncodedPath("wallet/getaccountnet")
            .build()

        val requestBody = buildJsonObject {
            put("address", tronAddress)
            put("visible", true)
        }

        val response = post(url, requestBody)
        val body = response.readBody()
        val json = JSONObject(body)

        val freeNetLimit = json.optLong("freeNetLimit", 0L)
        if (freeNetLimit <= 0) {
            return 0
        }

        val freeNetUsed = json.optLong("freeNetUsed", 0L)

        val available = freeNetLimit - freeNetUsed

        return if (available > 0) {
            available.toInt()
        } else {
            0
        }
    }

    private fun broadcastSignedTransaction(
        transaction: TronTransaction,
    ) {
        val builder = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("wallet/broadcasttransaction")
        val url = builder.build()

        val jsonBody = Json.parseToJsonElement(transaction.json.toString()).jsonObject
        val response = post(url, jsonBody)
        val body = response.readBody()
        val json = JSONObject(body)

        val ok = json.optBoolean("result")
        if (!ok) {
            val message = json.optString("message", "Broadcast failed")
            throw Exception("Broadcast failed: $message")
        }
    }


    fun estimateTransferResources(transfer: TronTransfer): TronResourcesEntity {
        var resources = applyResourcesSafetyMargin(estimateResources(transfer))
        val bandwidthAvailable = getAccountBandwidth(transfer.from)

        if (bandwidthAvailable > resources.bandwidth) {
            resources = resources.copy(
                bandwidth = 0
            )
        }

        return resources
    }

    fun estimateNativeTransferResources(
        from: String,
        to: String,
        amountSun: Long,
        bandwidthAvailableOverride: Int? = null,
    ): TronResourcesEntity {
        val transaction = runCatching {
            buildNativeTransfer(from = from, to = to, amountSun = 1L)
        }.getOrNull()

        val bandwidth = if (transaction != null) {
            estimateBandwidth(transaction.rawDataHex)
        } else {
            transferDefaultResources.bandwidth
        }

        var resources = applyResourcesSafetyMargin(
            TronResourcesEntity(energy = 0, bandwidth = bandwidth),
        )
        val bandwidthAvailable = bandwidthAvailableOverride ?: getAccountBandwidth(from)
        if (bandwidthAvailable > resources.bandwidth) {
            resources = resources.copy(bandwidth = 0)
        }
        return resources
    }

    fun buildNativeTransfer(
        from: String,
        to: String,
        amountSun: Long,
    ): TronTransaction {
        val url = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("wallet/createtransaction")
            .build()

        val requestBody = buildJsonObject {
            put("owner_address", from)
            put("to_address", to)
            put("amount", amountSun)
            put("visible", true)
        }

        val response = post(url, requestBody)
        val body = response.readBody()
        val json = JSONObject(body)

        if (!json.has("raw_data_hex")) {
            val message = json.optString("Error")
                .ifBlank { json.optString("message", "Failed to create TRX transfer") }
            throw Exception(message)
        }

        return TronTransaction(json = json)
    }

    fun getTransactionInfo(txId: String): JSONObject? {
        val url = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("wallet/gettransactioninfobyid")
            .build()

        val requestBody = buildJsonObject {
            put("value", txId)
        }

        val response = post(url, requestBody)
        val json = JSONObject(response.readBody())
        if (!json.has("id") && !json.has("blockNumber")) {
            return null
        }
        return json
    }

    suspend fun waitForSuccessfulTransaction(
        txId: String,
        pollIntervalMs: Long = 3_000L,
        timeoutMs: Long = 120_000L,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val info = getTransactionInfo(txId)
            if (info != null && (info.has("blockNumber") || info.has("id"))) {
                val result = info.optJSONObject("receipt")?.optString("result")
                    ?.takeIf { it.isNotBlank() }
                    ?: "SUCCESS"
                if (result != "SUCCESS") {
                    throw Exception("Transaction failed: $result")
                }
                return
            }
            delay(pollIntervalMs)
        }
        throw Exception("Timeout waiting for TRON transaction")
    }

    fun estimateBatteryCharges(
        transfer: TronTransfer,
        resources: TronResourcesEntity,
        auth: Authorization,
    ): TronEstimationEntity.Charges {
        val estimated = withRetry {
            batteryApi.tronEstimate(
                wallet = transfer.from,
                tonConnectAuth = auth.tonProof,
                xWalletId = auth.walletId,
                energy = resources.energy,
                bandwidth = resources.bandwidth
            )
        } ?: throw Exception("tron api failed")

        return TronEstimationEntity.Charges(
            charges = estimated.totalCharges,
            estimated = estimated,
        )
    }

    suspend fun estimateTrxFee(resources: TronResourcesEntity): TronEstimationEntity.TrxFee {
        val fee = getBurnTrxAmountForResources(resources)
        return TronEstimationEntity.TrxFee(fee = fee)
    }

    fun estimateTonFee(
        batteryEstimated: EstimatedTronTx,
    ): TronEstimationEntity.TonFee {
        val tonInstantFee = batteryEstimated.instantFee.acceptedAssets.find {
            it.type == EstimatedTronTxInstantFeeAcceptedAssetsInner.Type.ton
        } ?: throw Exception("Instant fee for ton not allowed")

        return TronEstimationEntity.TonFee(
            fee = Coins.ofNano(tonInstantFee.amountNano, TokenEntity.TON.decimals),
            sendToAddress = batteryEstimated.instantFee.feeAddress,
        )
    }

    fun sendWithTon(
        transaction: TronTransaction,
        instantFeeTx: Cell,
        resources: TronResourcesEntity,
        tronAddress: String,
        auth: Authorization,
        userPublicKey: String,
    ) {
        val base64 = transaction.json.toString().encodeBase64()
        val request = TronSendRequest(
            wallet = tronAddress,
            tx = base64,
            energy = resources.energy,
            bandwidth = resources.bandwidth,
            instantFeeTx = instantFeeTx.base64()
        )

        batteryApi.tronSend(
            tronSendRequest = request,
            tonConnectAuth = auth.tonProof,
            xWalletId = auth.walletId,
            userPublicKey = userPublicKey
        )
    }

    fun sendWithTrx(
        transaction: TronTransaction,
        resources: TronResourcesEntity,
        tronAddress: String,
    ) {
        broadcastSignedTransaction(transaction)
    }

    fun sendWithBattery(
        transaction: TronTransaction,
        resources: TronResourcesEntity,
        tronAddress: String,
        auth: Authorization,
    ) {
        val base64 = transaction.json.toString().encodeBase64()
        val request = TronSendRequest(
            wallet = tronAddress,
            tx = base64,
            energy = resources.energy,
            bandwidth = resources.bandwidth,
        )

        batteryApi.tronSend(
            tronSendRequest = request,
            tonConnectAuth = auth.tonProof,
            xWalletId = auth.walletId,
        )
    }

    fun buildSmartContractTransaction(transfer: TronTransfer): TronTransaction {
        val builder = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("wallet/triggersmartcontract")
        val url = builder.build()

        val requestBody = buildJsonObject {
            put("contract_address", transfer.contractAddress.tronHex())
            put("owner_address", transfer.from.tronHex())
            put("function_selector", transfer.function)
            put("parameter", transfer.data)
            put("call_value", 0)
            put("fee_limit", 150000000)
        }

        val response = post(url, requestBody)
        val body = response.readBody()
        val json = JSONObject(body)

        return TronTransaction(json = json.getJSONObject("transaction"))
    }

    private fun <R> tronRetry(retryBlock: () -> R) = withRetry(
        times = 3,
        backoff = ExponentialBackoff(minDelayMs = 100, maxDelayMs = 500)
    ) {
        retryBlock()
    }

    companion object {
        private const val DATA_HEX_PROTOBUF_EXTRA = 9
        private const val MAX_RESULT_SIZE_IN_TX = 64
        private const val A_SIGNATURE = 67
        private const val JSON_RPC_BALANCE_CHUNK = 25
    }
}

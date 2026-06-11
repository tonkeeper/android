package com.tonapps.wallet.api.tron

import android.net.Uri
import androidx.collection.arrayMapOf
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.blockchain.tron.encodeTronAddress
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.ton.cell.Cell
import java.math.BigInteger

class TronApi(
    private val config: ConfigEntity,
    private val okHttpClient: OkHttpClient,
    private val batteryApi: DefaultApi
) {

    companion object {
        private const val DATA_HEX_PROTOBUF_EXTRA = 9
        private const val MAX_RESULT_SIZE_IN_TX = 64
        private const val A_SIGNATURE = 67
        private const val RESOURCE_PRICES_KEY = "resource_prices"
    }

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
            return BalanceEntity(
                token = TokenEntity.TRON_USDT,
                value = Coins.of(BigInteger.ZERO, TokenEntity.TRON_USDT.decimals),
                walletAddress = tronAddress,
            )
        }
    }

    fun getTrxBalance(
        tronAddress: String,
    ): BalanceEntity {
        return try {
            val builder = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("v1/accounts/$tronAddress")
            val url = builder.build()

            val body = get(url)
            val json = JSONObject(body).getJSONArray("data")

            val balanceSun = json.optJSONObject(0)?.optLong("balance") ?: 0L

            return BalanceEntity(
                token = TokenEntity.TRX,
                value = Coins.of(balanceSun, TokenEntity.TRX.decimals),
                walletAddress = tronAddress,
            )
        } catch (_: Throwable) {
            BalanceEntity(
                token = TokenEntity.TRX,
                value = Coins.of(BigInteger.ZERO, TokenEntity.TRX.decimals),
                walletAddress = tronAddress,
            )
        }
    }

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
        batteryAuthToken: String,
        limit: Int,
        beforeTimestamp: Timestamp?,
    ): List<TronEventEntity> {
        val maxTimestamp = beforeTimestamp?.toLong()?.let { it - 1 }
        val response = withRetry {
            batteryApi.getTronTransactions(batteryAuthToken, limit, maxTimestamp)
        } ?: return emptyList()

        return response.transactions.filter { it.txid.isNotEmpty() }.map { TronEventEntity(it) }
    }

    suspend fun getTronHistory(
        tronAddress: String,
        tonProofToken: String,
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
                async { getBatteryTransfersHistory(tonProofToken, limit, beforeTimestamp) }
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
        if (freeNetLimit <= 0) return 0

        val freeNetUsed = json.optLong("freeNetUsed", 0L)

        val available = freeNetLimit - freeNetUsed

        return if (available > 0) available.toInt() else 0
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

    fun estimateBatteryCharges(
        transfer: TronTransfer,
        resources: TronResourcesEntity
    ): TronEstimationEntity.Charges {
        val estimated = withRetry {
            batteryApi.tronEstimate(
                wallet = transfer.from,
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
        batteryAuthToken: String,
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
            request,
            xTonConnectAuth = batteryAuthToken,
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
        tonProofToken: String
    ) {
        val base64 = transaction.json.toString().encodeBase64()
        val request = TronSendRequest(
            wallet = tronAddress,
            tx = base64,
            energy = resources.energy,
            bandwidth = resources.bandwidth,
        )

        batteryApi.tronSend(request, tonProofToken)
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
        backoff = ExponentialBackoff(minDelayMs = 1000, maxDelayMs = 32000)
    ) {
        retryBlock()
    }
}

package com.tonapps.wallet.api.tron

import androidx.core.net.toUri
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.blockchain.tron.encodeTronAddress
import com.tonapps.blockchain.tron.tronHex
import com.tonapps.extensions.map
import com.tonapps.icu.Coins
import com.tonapps.network.get
import com.tonapps.network.postJSON
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.api.tron.entity.TronEstimationEntity
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.api.withRetry
import io.batteryapi.apis.BatteryApi
import io.batteryapi.models.TronSendRequest
import io.ktor.util.encodeBase64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.math.BigInteger

class TronApi(
    private val config: ConfigEntity,
    private val okHttpClient: OkHttpClient,
    private val batteryApi: BatteryApi
) {

    companion object {
        private const val DATA_HEX_PROTOBUF_EXTRA = 9
        private const val MAX_RESULT_SIZE_IN_TX = 64
        private const val A_SIGNATURE = 67
    }

    private var safetyMargin: Double? = null

    fun getTronUsdtBalance(
        tronAddress: String,
    ): BalanceEntity {
        try {
            val builder = config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("wallet/triggersmartcontract")
            val url = builder.build().toString()

            val requestBody = buildJsonObject {
                put("owner_address", tronAddress.tronHex())
                put("contract_address", TokenEntity.TRON_USDT.address.tronHex())
                put("function_selector", "balanceOf(address)")
                put("parameter", tronAddress.encodeTronAddress())
            }

            val response = withRetry {
                okHttpClient.postJSON(url, requestBody.toString())
            } ?: throw Exception("tron api failed")
            val body = response.body?.string() ?: throw Exception("empty response")
            val json = JSONObject(body)

            val constantResultArray = json.optJSONArray("constant_result")
            val hexBalance = constantResultArray?.optString(0)
            val balance = hexBalance?.toBigInteger(16) ?: BigInteger.ZERO

            return BalanceEntity(
                token = TokenEntity.TRON_USDT,
                value = Coins.of(balance.toLong(), TokenEntity.TRON_USDT.decimals),
                walletAddress = tronAddress,
            )
        } catch (e: Throwable) {
            return BalanceEntity(
                token = TokenEntity.TRON_USDT,
                value = Coins.of(BigInteger.ZERO, TokenEntity.TRON_USDT.decimals),
                walletAddress = tronAddress,
            )
        }
    }


    private fun getTronBlockchainHistory(
        tronAddress: String,
        limit: Int,
        beforeLt: Long? = null
    ): List<TronEventEntity> {
        val builder = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("v1/accounts/$tronAddress/transactions/trc20")
            .appendQueryParameter("limit", limit.toString())
        beforeLt?.let {
            builder.appendQueryParameter("max_timestamp", (it * 1000 - 1).toString())
        }
        val url = builder.build().toString()

        val body = withRetry {
            okHttpClient.get(url)
        } ?: throw Exception("tron api failed")
        val json = JSONObject(body).getJSONArray("data")
        val events = json.map { TronEventEntity(it) }

        return events
    }

    private fun getBatteryTransfersHistory(
        batteryAuthToken: String,
        limit: Int,
        beforeLt: Long? = null
    ): List<TronEventEntity> {
        val maxTimestamp = beforeLt?.let { it * 1000 - 1 }
        val response = batteryApi.getTronTransactions(batteryAuthToken, limit, maxTimestamp);

        return response.transactions.filter { it.txid.isNotEmpty() }.map { TronEventEntity(it) }
    }

    fun getTronHistory(
        tronAddress: String,
        tonProofToken: String,
        limit: Int,
        beforeLt: Long?,
    ): List<TronEventEntity> {
        val blockchainEvents = getTronBlockchainHistory(tronAddress, limit, beforeLt)
        val batteryEvents = getBatteryTransfersHistory(tonProofToken, limit, beforeLt)

        return (batteryEvents + blockchainEvents).distinctBy { it.transactionHash }
            .sortedByDescending { it.timestamp }
    }

    private fun estimateResources(transfer: TronTransfer): TronResourcesEntity {
        val builder =
            config.tronApiUrl.toUri().buildUpon()
                .appendEncodedPath("wallet/triggerconstantcontract")
        val url = builder.build().toString()

        val requestBody = buildJsonObject {
            put("owner_address", transfer.from)
            put("contract_address", transfer.contractAddress)
            put("function_selector", transfer.function)
            put("parameter", transfer.data)
            put("visible", true)
        }

        val response = withRetry {
            okHttpClient.postJSON(url, requestBody.toString())
        } ?: throw Exception("tron api failed")
        val body = response.body?.string() ?: throw Exception("empty response")
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

        val bandwidth = (rawHex.length / 2) +
                DATA_HEX_PROTOBUF_EXTRA + MAX_RESULT_SIZE_IN_TX + A_SIGNATURE

        return TronResourcesEntity(energy = energy, bandwidth = bandwidth)
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

        return TronResourcesEntity(energy = energy, bandwidth = bandwidth)
    }

    private fun getAccountBandwidth(tronAddress: String): Int {
        val builder =
            config.tronApiUrl.toUri().buildUpon().appendEncodedPath("v1/accounts/$tronAddress")
        val url = builder.build().toString()

        val body = withRetry {
            okHttpClient.get(url)
        } ?: throw Exception("tron api failed")
        val json = JSONObject(body)

        val dataArray = json.optJSONArray("data")

        val info = dataArray.optJSONObject(0)
        return info?.optInt("free_net_usage", 0) ?: 0
    }

    fun estimateBatteryCharges(transfer: TronTransfer): TronEstimationEntity {
        var resources = applyResourcesSafetyMargin(estimateResources(transfer))
        val bandwidthAvailable = getAccountBandwidth(transfer.from)
        resources = resources.copy(
            bandwidth = kotlin.math.max(0, resources.bandwidth - bandwidthAvailable)
        )

        val estimation = withRetry {
            batteryApi.tronEstimate(
                wallet = transfer.from,
                energy = resources.energy,
                bandwidth = resources.bandwidth
            )
        } ?: throw Exception("tron api failed")

        return TronEstimationEntity(
            charges = estimation.totalCharges,
            resources = resources,
        )
    }

    fun sendTransaction(
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

        batteryApi.tronSend(tonProofToken, request)
    }

    fun buildSmartContractTransaction(transfer: TronTransfer): TronTransaction {
        val builder = config.tronApiUrl.toUri().buildUpon()
            .appendEncodedPath("wallet/triggersmartcontract")
        val url = builder.build().toString()

        val requestBody = buildJsonObject {
            put("contract_address", transfer.contractAddress.tronHex())
            put("owner_address", transfer.from.tronHex())
            put("function_selector", transfer.function)
            put("parameter", transfer.data)
            put("call_value", 0)
            put("fee_limit", 150000000)
        }

        val response = withRetry {
            okHttpClient.postJSON(url, requestBody.toString())
        } ?: throw Exception("tron api failed")
        val body = response.body?.string() ?: throw Exception("empty response")
        val json = JSONObject(body)

        return TronTransaction(json = json.getJSONObject("transaction"))
    }

    fun activateWallet(
        tronAddress: String,
        tonProofToken: String,
    ) {
        batteryApi.tronSend(tonProofToken, TronSendRequest(wallet = tronAddress, tx = ""))
    }

}
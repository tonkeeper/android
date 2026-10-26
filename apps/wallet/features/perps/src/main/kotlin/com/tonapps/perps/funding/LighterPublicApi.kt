package com.tonapps.perps.funding

import com.tonapps.async.Async
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

private const val LIGHTER_MAINNET_URL = "https://mainnet.zklighter.elliot.ai"

enum class LighterTransactionPresence {
    KNOWN,
    FAILED,
    UNKNOWN,
}

class LighterTransaction(
    val presence: LighterTransactionPresence,
    val executedAtMs: Long?,
)

class LighterAssetMinimums(
    val withdrawal: BigDecimal?,
    val transfer: BigDecimal?,
)

class LighterPublicApi(
    private val client: OkHttpClient,
    private val baseUrl: String = LIGHTER_MAINNET_URL,
) {

    suspend fun withdrawalDelaySeconds(): Long {
        val body = get("/api/v1/withdrawalDelay")
        return body.jsonObject["seconds"]?.jsonPrimitive?.longOrNull
            ?: throw IOException("lighter: withdrawalDelay without seconds")
    }

    suspend fun assetMinimums(assetId: Int): LighterAssetMinimums? {
        val body = get("/api/v1/assetDetails")
        val asset = body.jsonObject["asset_details"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["asset_id"]?.jsonPrimitive?.longOrNull == assetId.toLong() }
            ?: return null
        return LighterAssetMinimums(
            withdrawal = asset["min_withdrawal_amount"]?.jsonPrimitive?.content?.toBigDecimalOrNull(),
            transfer = asset["min_transfer_amount"]?.jsonPrimitive?.content?.toBigDecimalOrNull(),
        )
    }

    suspend fun transferFeeUnits(accountIndex: Long, toAccountIndex: Long? = null): BigInteger {
        val recipient = toAccountIndex?.let { "&to_account_index=$it" }.orEmpty()
        val body = get("/api/v1/transferFeeInfo?account_index=$accountIndex$recipient")
        val fee = body.jsonObject["transfer_fee_usdc"]?.jsonPrimitive?.longOrNull
            ?: throw IOException("lighter: transferFeeInfo without transfer_fee_usdc")
        return BigInteger.valueOf(fee)
    }

    suspend fun transaction(hash: String): LighterTransaction {
        val tx = get("/api/v1/tx?by=hash&value=${hash.removePrefix(HEX_PREFIX)}", allowErrorEnvelope = true).jsonObject
        val code = tx["code"]?.jsonPrimitive?.longOrNull
            ?: throw IOException("lighter: tx lookup answered without a code")
        return when (code) {
            OK_CODE -> LighterTransaction(
                presence = if (tx["status"]?.jsonPrimitive?.longOrNull == TX_STATUS_FAILED) {
                    LighterTransactionPresence.FAILED
                } else {
                    LighterTransactionPresence.KNOWN
                },
                executedAtMs = tx["executed_at"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 },
            )

            NOT_FOUND_CODE -> LighterTransaction(LighterTransactionPresence.UNKNOWN, executedAtMs = null)
            else -> throw IOException("lighter: tx lookup answered $code")
        }
    }

    private suspend fun get(path: String, allowErrorEnvelope: Boolean = false) = withContext(Async.Io) {
        val request = Request.Builder().url(baseUrl + path).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("lighter: empty body for $path")
            val envelope = if (allowErrorEnvelope) {
                parseOrNull(text)?.takeIf { it is JsonObject && it.containsKey("code") }
            } else {
                null
            }
            if (!response.isSuccessful && envelope == null) {
                throw IOException("lighter: HTTP ${response.code} for $path")
            }
            envelope ?: parseOrNull(text) ?: throw IOException("lighter: unreadable body for $path")
        }
    }

    private fun parseOrNull(text: String): JsonElement? {
        return try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            null
        }
    }

    private companion object {
        const val HEX_PREFIX = "0x"
        const val OK_CODE = 200L
        const val NOT_FOUND_CODE = 21500L

        // https://github.com/elliottech/lighter-ts/blob/d0493ae8c3499e94c1810d1256f317a6bbff6c20/src/constants/shared.ts#L22-L29
        const val TX_STATUS_FAILED = 0L
        val json = Json { ignoreUnknownKeys = true }
    }
}

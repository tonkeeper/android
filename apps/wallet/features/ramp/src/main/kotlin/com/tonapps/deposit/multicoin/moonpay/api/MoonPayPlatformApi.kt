package com.tonapps.deposit.multicoin.moonpay.api

import com.tonapps.deposit.multicoin.catchingResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.Date
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

// Platform API calls the app makes directly. Customer scoped: every call takes the access token
// of an active connection. The environment must match the one the session was created in.
class MoonPayPlatformApi(
    private val client: OkHttpClient,
    private val environment: MoonPayEnvironment,
    private val timeSource: TimeSource.WithComparableMarks = ElapsedRealtimeTimeSource,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun buyQuote(accessToken: String, request: MoonPayBuyQuoteRequest): Result<MoonPayBuyQuote> = catchingResult {
        val body = json.encodeToString(MoonPayBuyQuoteRequest.serializer(), request).toRequestBody(JSON_MEDIA_TYPE)
        val response = perform(url(QUOTES, BUY).build(), accessToken, body)
        val quote = decode<MoonPayBuyQuoteResponse>(response.body).data
        quote.copy(
            expiresAtMark = expiresAtMark(quote, response),
            serverClockOffsetMs = response.serverDate?.let { it.time - wallClockMs() },
        )
    }

    // `currency` (ISO 4217) narrows the configurations to one fiat currency.
    suspend fun paymentMethods(accessToken: String, currency: String? = null): Result<MoonPayPaymentMethods> = catchingResult {
        val url = url(PAYMENT_METHODS)
            .apply { currency?.let { addQueryParameter("currency", it) } }
            .build()
        val response = perform(url, accessToken)
        decode<MoonPayPaymentMethodsResponse>(response.body).toPaymentMethods()
    }

    // Removes a saved card. MoonPay enables removal per account: a 404 is a card already gone or removal turned off.
    suspend fun deletePaymentMethod(accessToken: String, id: String): Result<Unit> = catchingResult {
        perform(url(PAYMENT_METHODS, id).build(), accessToken, delete = true)
        Unit
    }

    suspend fun assets(
        accessToken: String,
        source: MoonPayAssetSource? = null,
        code: String? = null,
        cursor: String? = null,
        limit: Int = DEFAULT_ASSETS_LIMIT,
    ): Result<MoonPayAssetsPage> = catchingResult {
        val url = url(ASSETS)
            .addQueryParameter("limit", limit.toString())
            .apply {
                source?.let { addQueryParameter("source", it.value) }
                code?.let { addQueryParameter("code", it) }
                cursor?.let { addQueryParameter("cursor", it) }
            }
            .build()
        val response = decode<MoonPayAssetsResponse>(perform(url, accessToken).body)
        MoonPayAssetsPage(response.data, response.pageInfo?.nextCursor)
    }

    suspend fun transaction(accessToken: String, id: String): Result<MoonPayTransactionDetails> = catchingResult {
        val response = perform(url(TRANSACTIONS, id).build(), accessToken)
        decode<MoonPayTransactionResponse>(response.body).data
    }

    // The first page of the customer's transactions created at or after `startDateMs`, on MoonPay's clock.
    suspend fun transactions(
        accessToken: String,
        startDateMs: Long,
        limit: Int = DEFAULT_TRANSACTIONS_LIMIT,
    ): Result<List<MoonPayTransactionDetails>> = catchingResult {
        val url = url(TRANSACTIONS)
            .addQueryParameter("startDate", Instant.ofEpochMilli(startDateMs).toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        decode<MoonPayTransactionsResponse>(perform(url, accessToken).body).data
    }

    private fun url(vararg segments: String): HttpUrl.Builder {
        val builder = environment.apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment(PLATFORM)
            .addPathSegment(VERSION)
        segments.forEach(builder::addPathSegment)
        return builder
    }

    private suspend fun perform(url: HttpUrl, accessToken: String, body: RequestBody? = null, delete: Boolean = false): RawResponse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            // Saved cards and wallet addresses must not land in the client's disk cache.
            .cacheControl(NO_STORE)
            .apply { body?.let(::post) }
            .apply {
                if (delete) {
                    delete()
                }
            }
            .build()
        val response = try {
            client.newCall(request).await { response ->
                val receivedAt = timeSource.markNow()
                RawResponse(
                    code = response.code,
                    body = response.body.string(),
                    serverDate = response.headers.getDate("Date"),
                    receivedAt = receivedAt,
                )
            }
        } catch (e: IOException) {
            throw MoonPayApiException.Transport(e)
        }
        if (response.code !in HTTP_SUCCESS) {
            val error = try {
                json.decodeFromString(MoonPayServerError.serializer(), response.body)
            } catch (_: SerializationException) {
                null
            }
            throw if (response.code == HTTP_TOO_MANY_REQUESTS) {
                MoonPayApiException.RateLimited(error)
            } else {
                MoonPayApiException.Http(response.code, error)
            }
        }
        return response
    }

    // Both ends of the remaining time are server time, so the device's wall clock never enters it.
    private fun expiresAtMark(quote: MoonPayBuyQuote, response: RawResponse): ComparableTimeMark? {
        val serverDate = response.serverDate ?: return null
        val expiresAt = quote.expiresAt?.let(::parseMoonPayInstantMs) ?: return null
        return response.receivedAt + (expiresAt - serverDate.time).milliseconds
    }

    private inline fun <reified T> decode(body: String): T {
        return try {
            json.decodeFromString<T>(body)
        } catch (_: SerializationException) {
            throw MoonPayApiException.Decoding(T::class.simpleName)
        }
    }

    private class RawResponse(
        val code: Int,
        val body: String,
        val serverDate: Date?,
        val receivedAt: ComparableTimeMark,
    )

    private companion object {
        const val PLATFORM = "platform"
        const val VERSION = "v1"
        const val QUOTES = "quotes"
        const val BUY = "buy"
        const val PAYMENT_METHODS = "payment-methods"
        const val ASSETS = "assets"
        const val TRANSACTIONS = "transactions"
        const val DEFAULT_ASSETS_LIMIT = 50
        const val DEFAULT_TRANSACTIONS_LIMIT = 50
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SUCCESS = 200..299
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val NO_STORE = CacheControl.Builder().noStore().build()

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}

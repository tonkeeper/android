package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class MoonPayPlatformApiTest {

    private var answer = Answer(200, "")
    private var recorded: Request? = null
    private var recordedBody: String? = null
    private var onRequest: (Interceptor.Chain) -> Unit = {}
    private val timeSource = TestTimeSource()

    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            recorded = request
            recordedBody = request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }
            onRequest(chain)
            val builder = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(answer.code)
                .message("")
                .body(answer.body.toResponseBody(JSON))
            answer.headers.forEach { (name, value) -> builder.header(name, value) }
            builder.build()
        })
        .build()

    private val api = MoonPayPlatformApi(client, MoonPayEnvironment.Sandbox, timeSource)

    @Test
    fun `buy quote sends a bearer token and decodes the documented response`() = runBlocking {
        respond(
            200,
            """
            {"data":{
                "source":{"amount":"100.00","asset":{"code":"USD"}},
                "destination":{"amount":"0.00114","asset":{"code":"BTC"}},
                "fees":{
                    "network":{"amount":"2.39","asset":{"code":"USD"}},
                    "moonpay":{"amount":"4.99","asset":{"code":"USD"}},
                    "ecosystem":{"amount":"1.00","asset":{"code":"USD"}}
                },
                "wallet":{"address":"0x1234"},
                "paymentMethod":{"type":"apple_pay"},
                "feeBehavior":"inclusive",
                "expiresAt":"2026-03-27T12:30:00.000Z",
                "executable":true,
                "exchangeRate":87523.17,
                "exchangeRateType":"fixed",
                "signature":"cXVvdGUuc2lnbmF0dXJl",
                "paymentDisclosures":[{"id":"eea-crypto-asset-risk","version":1}]
            }}
            """,
        )
        val quoteRequest = MoonPayBuyQuoteRequest(
            source = MoonPayMoney(amount = "100", asset = MoonPayAsset(code = "USD")),
            destination = MoonPayMoney(asset = MoonPayAsset(code = "BTC")),
            wallet = MoonPayWalletReference(address = "0x1234"),
            paymentMethod = MoonPayPaymentMethodReference(type = "apple_pay"),
        )

        val quote = api.buyQuote("acc_1", quoteRequest).getOrThrow()

        val request = recorded!!
        assertEquals("POST", request.method)
        assertEquals("https://api.moonpay.dev/platform/v1/quotes/buy", request.url.toString())
        assertEquals("Bearer acc_1", request.header("Authorization"))
        assertEquals("application/json", request.body?.contentType()?.let { "${it.type}/${it.subtype}" })
        assertEquals(
            Json.parseToJsonElement(
                """{"destination":{"asset":{"code":"BTC"}},"paymentMethod":{"type":"apple_pay"},"source":{"amount":"100","asset":{"code":"USD"}},"wallet":{"address":"0x1234"}}""",
            ),
            Json.parseToJsonElement(recordedBody!!),
        )

        assertEquals("cXVvdGUuc2lnbmF0dXJl", quote.signature)
        assertTrue(quote.executable)
        assertEquals("0.00114", quote.destination.amount)
        assertEquals("BTC", quote.destination.asset.code)
        assertEquals("2.39", quote.fees?.get("network")?.amount)
        assertEquals(3, quote.fees?.size)
        assertEquals("87523.17", quote.exchangeRate)
        assertEquals("fixed", quote.exchangeRateType)
        assertEquals(MoonPayFeeBehavior.Inclusive, quote.feeBehavior)
        assertEquals("2026-03-27T12:30:00.000Z", quote.expiresAt)
        assertEquals("eea-crypto-asset-risk", quote.paymentDisclosures?.first()?.id)
        assertEquals("1", quote.paymentDisclosures?.first()?.version)
        assertNull(quote.challengeUrl)
    }

    @Test
    fun `quote lifetime runs on the server clock and the monotonic clock`() = runBlocking {
        // The server says it is 12:29:50 and the quote expires 10 s later, whatever this device's clock says.
        respond(
            200,
            """{"data":{"source":{"asset":{"code":"USD"}},"destination":{"asset":{"code":"TON"}},"executable":true,"signature":"s","expiresAt":"2026-03-27T12:30:00.000Z"}}""",
            "Date" to "Fri, 27 Mar 2026 12:29:50 GMT",
        )

        val quote = api.buyQuote("acc_1", quoteRequest()).getOrThrow()

        assertEquals(10.seconds, quote.lifetime)
        timeSource += 4.seconds
        assertEquals(6.seconds, quote.lifetime)
        assertTrue(quote.isFresh(5.seconds))
        assertFalse(quote.isFresh(7.seconds))
    }

    @Test
    fun `quote lifetime without server time is unknown`() = runBlocking {
        respond(
            200,
            """{"data":{"source":{"asset":{"code":"USD"}},"destination":{"asset":{"code":"TON"}},"executable":true,"signature":"s","expiresAt":"2026-03-27T12:30:00.000Z"}}""",
        )

        val quote = api.buyQuote("acc_1", quoteRequest()).getOrThrow()

        assertNull(quote.lifetime)
        assertTrue(quote.isFresh(5.seconds))
    }

    @Test
    fun `quote challenge url`() = runBlocking {
        respond(
            200,
            """{"data":{"source":{"asset":{"code":"USD"}},"destination":{"asset":{"code":"TON"}},"executable":false,"signature":"s","challenge":{"url":"https://platform.moonpay.com/platform/v1/challenge?challengeToken=t"}}}""",
        )

        val quote = api.buyQuote("acc", quoteRequest()).getOrThrow()

        assertFalse(quote.executable)
        assertEquals("https://platform.moonpay.com/platform/v1/challenge?challengeToken=t", quote.challengeUrl)
    }

    @Test
    fun `assets send a bearer GET and decode a page`() = runBlocking {
        respond(
            200,
            """
            {"data":[
                {"caip19":"eip155:1/slip44:60","code":"ETH","name":"Ethereum","network":"ethereum","source":"cefi","contractAddress":null,"decimals":18,"precision":6,"status":{"suspended":false,"disabled":false}},
                {"caip19":"tron:0x2b6653dc/trc20:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","code":"USDT_TRX","name":"Tether (Tron)","network":"tron","source":"cefi","contractAddress":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","decimals":6,"precision":2,"status":{"suspended":true,"disabled":false}},
                {"caip19":"eip155:8453/erc20:0xabc","code":"AERO","network":"base","source":"defi"}
            ],"pageInfo":{"nextCursor":"next_1"}}
            """,
        )

        val page = api.assets("acc_1", source = MoonPayAssetSource.Cefi, cursor = "c0", limit = 2).getOrThrow()

        val request = recorded!!
        assertEquals("GET", request.method)
        assertEquals("/platform/v1/assets", request.url.encodedPath)
        assertEquals("cefi", request.url.queryParameter("source"))
        assertEquals("c0", request.url.queryParameter("cursor"))
        assertEquals("2", request.url.queryParameter("limit"))
        assertNull(request.url.queryParameter("code"))
        assertEquals("Bearer acc_1", request.header("Authorization"))
        assertEquals("next_1", page.nextCursor)
        assertEquals(listOf("ETH", "USDT_TRX", "AERO"), page.assets.map { it.code })
        assertNull(page.assets[0].contractAddress)
        assertTrue(page.assets[0].isAvailable)
        assertFalse(page.assets[1].isAvailable)
        assertEquals(MoonPayAsset(code = "USDT_TRX"), page.assets[1].quoteReference)
        assertEquals(MoonPayAsset(caip19 = "eip155:8453/erc20:0xabc"), page.assets[2].quoteReference)
    }

    @Test
    fun `transaction sends a bearer GET and decodes stages`() = runBlocking {
        respond(
            200,
            """
            {"data":{
                "id":"txn_1","status":"failed","transactionHash":null,
                "customer":{"id":"cus_9"},
                "stages":[
                    {"kind":"ordering","name":"Ordering","status":"success","failureReason":null},
                    {"kind":"waiting_authentication","name":"Authentication","status":"failed","failureReason":"three_ds_failure"}
                ]
            }}
            """,
        )

        val transaction = api.transaction("acc_1", "txn_1").getOrThrow()

        val request = recorded!!
        assertEquals("GET", request.method)
        assertEquals("https://api.moonpay.dev/platform/v1/transactions/txn_1", request.url.toString())
        assertEquals("Bearer acc_1", request.header("Authorization"))
        assertEquals("txn_1", transaction.id)
        assertTrue(transaction.isFailed)
        assertFalse(transaction.isCompleted)
        assertEquals("cus_9", transaction.customer?.id)
        assertEquals("three_ds_failure", transaction.failedStage?.failureReason)
    }

    @Test
    fun `payment methods send a bearer GET and decode the documented response`() = runBlocking {
        respond(
            200,
            """
            {"data":{
                "paymentMethodConfigs":[
                    {"type":"google_pay","capabilities":{"supportedCurrencies":["USD","EUR"],"supportedTransactionTypes":["buy"],"allowsDeletion":false,"requiresWidget":false},"availability":{"active":true,"reasons":[]}},
                    {"type":"card","capabilities":{"supportedCurrencies":["USD"],"supportedTransactionTypes":["buy","sell"],"allowsDeletion":true,"requiresWidget":false},"availability":{"active":true,"reasons":[]}},
                    {"type":"sepa","capabilities":{"supportedCurrencies":["EUR"],"supportedTransactionTypes":["buy"],"allowsDeletion":false,"requiresWidget":true},"availability":{"active":false,"reasons":["unsupported_region"]}}
                ],
                "paymentMethods":[
                    {"id":"a1b2","type":"card","cardType":"credit","brand":"visa","last4":"4242","expirationMonth":"03","expirationYear":"2028","availability":{"active":true,"reasons":[]}},
                    {"id":"c3d4","type":"card","cardType":"debit","brand":"mastercard","last4":"8910","expirationMonth":"01","expirationYear":"2024","availability":{"active":false,"reasons":["card_expired"]}},
                    {"id":"google_pay_fakeid","type":"google_pay"}
                ]
            }}
            """,
        )

        val methods = api.paymentMethods("acc_1", currency = "USD").getOrThrow()

        val request = recorded!!
        assertEquals("GET", request.method)
        assertEquals("https://api.moonpay.dev/platform/v1/payment-methods?currency=USD", request.url.toString())
        assertEquals("Bearer acc_1", request.header("Authorization"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertNull(request.body)
        assertEquals(listOf("google_pay", "card", "sepa"), methods.configs.map { it.type })
        assertEquals(listOf("google_pay", "card"), methods.activeConfigs.map { it.type })
        assertEquals(listOf("USD", "EUR"), methods.configs[0].capabilities?.supportedCurrencies)
        assertEquals(true, methods.configs[1].capabilities?.allowsDeletion)
        assertTrue(methods.configs[1].supportsBuy)
        assertEquals(true, methods.configs[2].capabilities?.requiresWidget)
        assertEquals(listOf("unsupported_region"), methods.configs[2].availability?.reasons)
        assertEquals(listOf("4242", "8910"), methods.stored.map { it.last4 })
        assertEquals(listOf("a1b2"), methods.stored.filter { it.availability?.active == true }.map { it.id })
        assertEquals("03", methods.stored[0].expirationMonth)
        assertEquals(
            MoonPayPaymentMethodAvailability(active = false, reasons = listOf("card_expired")),
            methods.stored[1].availability,
        )
    }

    @Test
    fun `payment methods without currency omit the query and tolerate a sparse response`() = runBlocking {
        respond(200, """{"data":{"paymentMethodConfigs":[{"type":"pix"}]}}""")

        val methods = api.paymentMethods("acc_1").getOrThrow()

        assertEquals("https://api.moonpay.dev/platform/v1/payment-methods", recorded!!.url.toString())
        assertNull(recorded!!.url.query)
        assertEquals(listOf("pix"), methods.configs.map { it.type })
        assertNull(methods.configs[0].capabilities)
        assertNull(methods.configs[0].availability)
        assertFalse(methods.configs[0].isActive)
        assertTrue(methods.configs[0].supportsBuy)
        assertEquals(emptyList<MoonPaySavedCard>(), methods.stored)
    }

    @Test
    fun `http error carries the server body`() = runBlocking {
        respond(
            400,
            """{"code":"validationError","message":"Invalid request","errors":[{"field":"wallet.address","message":"required"}]}""",
        )

        val error = quoteFailure() as MoonPayApiException.Http

        assertEquals(400, error.status)
        assertEquals("validationError", error.body?.code)
        assertEquals("wallet.address", error.body?.errors?.first()?.field)
        assertEquals("MoonPay API HTTP 400 (validationError — Invalid request — wallet.address: required)", error.message)
        assertNull(error.buyAmountLimit)
    }

    @Test
    fun `quote amount limit is read from the error details`() = runBlocking {
        respond(
            400,
            """{"code":"invalid_request","message":"Invalid request","errors":[{"field":"source.amount","message":"Source amount is too low. Minimum is 20 USD","details":{"minBuyAmount":"20"}}]}""",
        )
        assertEquals(
            MoonPayBuyAmountLimit.BelowMinimum(BigDecimal("20")),
            (quoteFailure() as MoonPayApiException.Http).buyAmountLimit,
        )

        respond(
            400,
            """{"code":"invalid_request","message":"Invalid request","errors":[{"field":"source.amount","message":"Source amount is too high. Maximum is 29850 USD","details":{"maxBuyAmount":29850}}]}""",
        )
        assertEquals(
            MoonPayBuyAmountLimit.AboveMaximum(BigDecimal("29850")),
            (quoteFailure() as MoonPayApiException.Http).buyAmountLimit,
        )
    }

    @Test
    fun `rate limit`() = runBlocking {
        respond(429, "")

        assertTrue(quoteFailure() is MoonPayApiException.RateLimited)
    }

    @Test
    fun `decoding failure is reported`() = runBlocking {
        respond(200, """{"unexpected":true}""")

        assertTrue(quoteFailure() is MoonPayApiException.Decoding)
    }

    @Test
    fun `server error with a non json body keeps the status`() = runBlocking {
        respond(502, "<html><body>Bad Gateway</body></html>")

        val error = quoteFailure() as MoonPayApiException.Http

        assertEquals(502, error.status)
        assertNull(error.body)
    }

    @Test
    fun `network failure is a transport error`() = runBlocking {
        onRequest = { throw IOException("offline") }

        val error = quoteFailure() as MoonPayApiException.Transport

        assertEquals("offline", error.cause?.message)
    }

    @Test
    fun `cancelling the coroutine cancels the http call`() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        onRequest = { chain ->
            started.countDown()
            while (!chain.call().isCanceled()) {
                Thread.sleep(POLL_MS)
            }
            cancelled.countDown()
        }

        val call = async(Dispatchers.Default) { api.transaction("acc_1", "txn_1") }
        assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS))
        call.cancel()

        assertTrue(cancelled.await(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    private suspend fun quoteFailure(): Throwable {
        return api.buyQuote("acc_1", quoteRequest()).exceptionOrNull() ?: error("expected an error")
    }

    private fun quoteRequest(): MoonPayBuyQuoteRequest {
        return MoonPayBuyQuoteRequest(
            source = MoonPayMoney(amount = "1", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(asset = MoonPayAsset(code = "ton")),
            wallet = MoonPayWalletReference(address = "0x1"),
        )
    }

    private fun respond(code: Int, body: String, vararg headers: Pair<String, String>) {
        answer = Answer(code, body.trimIndent(), headers.toList())
    }

    private class Answer(val code: Int, val body: String, val headers: List<Pair<String, String>> = emptyList())

    private companion object {
        val JSON = "application/json".toMediaType()
        const val POLL_MS = 10L
        const val WAIT_SECONDS = 5L
    }
}

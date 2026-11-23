package com.tonapps.deposit.multicoin.domain

import com.tonapps.wallet.testing.TestWallet
import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayAssetRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnection
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionRepository
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuoteRequest
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayErrorDetail
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodAvailability
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodConfig
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethods
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPaySavedCard
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayServerError
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayWalletReference
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.testing.TestAssets
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.MoonPayPaymentMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class MoonPayPaymentsTest {

    private val connection = MoonPayConnection(
        environment = MoonPayEnvironment.Production,
        credentials = MoonPayCredentials("access", "client", "2030-01-01T00:00:00Z"),
    )
    private val usdtTon = MoonPayPlatformAsset(code = "usdt_ton", network = "ton")
    private val clock = TestTimeSource()

    private val connections = mockk<MoonPayConnectionRepository>(relaxUnitFun = true) {
        coEvery { ensureConnected(any(), any()) } returns Result.success(connection)
    }
    private val assets = mockk<MoonPayAssetRepository> {
        coEvery { find(connection, TestAssets.usdtTon.id, "USDT") } returns Result.success(usdtTon)
    }
    private val api = mockk<MoonPayPlatformApi> {
        coEvery { paymentMethods("access", "eur") } returns Result.success(
            MoonPayPaymentMethods(
                configs = listOf(config("card"), config("google_pay"), config("apple_pay"), config("revolut_pay"), config("sepa", types = listOf("sell")), config("paypal", active = false)),
                stored = listOf(
                    MoonPaySavedCard(id = "expired", type = "card", availability = MoonPayPaymentMethodAvailability(active = false)),
                    MoonPaySavedCard(id = "visa", type = "card", availability = MoonPayPaymentMethodAvailability(active = true)),
                ),
            ),
        )
    }
    private val rampRepository = mockk<RampRepository> {
        coEvery { getAsset(TestWallet.ID, RampType.RampOn, TestAssets.usdtTon.id, "EUR") } returns detail(
            RampMethod(
                type = ExchangePaymentMethodType.p2p,
                name = "P2P Express",
                image = "",
                providers = listOf(RampProvider(ExchangeMerchantSlug.wallet, "EUR", ExchangeLimits())),
            ),
        )
    }
    private val exchangeRepository = mockk<ExchangeRepository> {
        coEvery { getMoonPayPaymentMethods() } returns listOf(MoonPayPaymentMethod(type = "card", title = "Debit card", subtitle = "Instant"))
    }
    private val walletAddress = mockk<RampWalletAddress> {
        coEvery { of(TestWallet.ID, TestAssets.usdtTon.id) } returns "UQwallet"
    }
    private val frames = MoonPayFrames()

    private val removedCards = MoonPayRemovedCards()
    private val payments = MoonPayPayments(
        walletId = TestWallet.ID,
        connections = connections,
        assets = assets,
        frames = frames,
        rampRepository = rampRepository,
        exchangeRepository = exchangeRepository,
        walletAddress = walletAddress,
        platformApi = { api },
        theme = MoonPayTheme.Dark,
        string = { id -> "string:$id" },
        removedCards = removedCards,
        methodSupport = MoonPayMethodSupport(googlePayEnabled = { false }, deviceSupportsGooglePay = { true }),
    )

    @Before
    fun attachHost() {
        frames.attachHost(Any())
    }

    @Test
    fun `the methods are MoonPay's active ones that buy, without Google or Apple Pay, named by us, with P2P last`() = runTest {
        val methods = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty().filter { it.card == null }

        assertEquals(listOf("card", "revolut_pay", "p2p"), methods.map { it.id })
        assertEquals(listOf("Debit card", "Revolut Pay", "P2P Express"), methods.map { it.title })
        assertEquals("Instant", methods.first().subtitle)
    }

    @Test
    fun `saved cards come first, each its own method, and the card method is a new card`() = runTest {
        val methods = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals(listOf("card:expired", "card:visa", "card", "revolut_pay", "p2p"), methods.map { it.id })
        val visa = methods[1].details as RampPaymentMethod.Details.MoonPay
        assertEquals("visa", visa.cardId)
        assertEquals(MoonPayAsset(code = "usdt_ton"), visa.destination)
        assertEquals(listOf(false, true), methods.take(2).map { it.selectable })
        assertTrue(methods[2].isNewCard)
        assertNull((methods[3].details as RampPaymentMethod.Details.MoonPay).cardId)
    }

    @Test
    fun `a saved card is shown by its kind, brand and last digits`() = runTest {
        coEvery { api.paymentMethods("access", "eur") } returns Result.success(
            MoonPayPaymentMethods(
                configs = listOf(config("card")),
                stored = listOf(
                    MoonPaySavedCard(id = "a", type = "card", cardType = "credit", brand = "VISA", last4 = "4148", expirationMonth = "10", expirationYear = "2028"),
                    MoonPaySavedCard(id = "b", type = "card"),
                    MoonPaySavedCard(id = "c", type = "card", brand = "american express", last4 = "1005"),
                ),
            ),
        )

        val (credit, bare, amex) = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals("string:${Localization.moonpay_card_credit}", credit.title)
        assertEquals("Visa •••• 4148", credit.subtitle)
        assertEquals(RampSavedCard("a", "credit", "VISA", "4148", 10, 2028, active = true, removable = false), credit.card)
        assertEquals("string:${Localization.moonpay_card}", bare.title)
        assertEquals("string:${Localization.moonpay_card} •••• ••••", bare.subtitle)
        assertEquals("American Express •••• 1005", amex.subtitle)
    }

    @Test
    fun `saved cards only show while MoonPay takes cards, removable as its card config says`() = runTest {
        val noRemoval = config("card").copy(capabilities = MoonPayPaymentMethodConfig.Capabilities(supportedTransactionTypes = listOf("buy"), allowsDeletion = false))
        val visa = MoonPaySavedCard(id = "visa", type = "card")
        coEvery { api.paymentMethods("access", "eur") } returnsMany listOf(
            Result.success(MoonPayPaymentMethods(configs = listOf(config("revolut_pay")), stored = listOf(visa))),
            Result.success(MoonPayPaymentMethods(configs = listOf(noRemoval), stored = listOf(visa))),
        )

        val withoutCards = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()
        val withCards = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals(listOf("revolut_pay", "p2p"), withoutCards.map { it.id })
        assertEquals(false, withCards.first().card?.removable)
    }

    @Test
    fun `a card whose removal started is out of every load, and back if the removal fails`() = runTest {
        val removal = CompletableDeferred<Result<Unit>>()
        coEvery { api.deletePaymentMethod("access", "visa") } coAnswers { removal.await() }

        val removing = async { payments.removeCard("visa") }
        runCurrent()
        val during = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()
        removal.complete(Result.failure(MoonPayApiException.Http(500, null)))
        removing.await()
        val after = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals(listOf("card:expired", "card"), during.map { it.id }.take(2))
        assertEquals(listOf("card:expired", "card:visa"), after.map { it.id }.take(2))
    }

    @Test
    fun `a removal cut short by a cancellation gives the card back`() = runTest {
        coEvery { api.deletePaymentMethod("access", "visa") } coAnswers { throw CancellationException("timed out") }

        runCatching { payments.removeCard("visa") }
        val after = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertTrue(after.any { it.id == "card:visa" })
    }

    @Test
    fun `a card is removed on MoonPay, one already gone counts as removed`() = runTest {
        coEvery { api.deletePaymentMethod("access", "visa") } returns Result.success(Unit)
        coEvery { api.deletePaymentMethod("access", "gone") } returns Result.failure(MoonPayApiException.Http(404, null))
        coEvery { api.deletePaymentMethod("access", "down") } returns Result.failure(MoonPayApiException.Http(500, null))

        assertTrue(payments.removeCard("visa").isSuccess)
        assertTrue(payments.removeCard("gone").isSuccess)
        assertTrue(payments.removeCard("down").isFailure)
    }

    @Test
    fun `an asset MoonPay does not sell goes to the merchants`() = runTest {
        coEvery { assets.find(connection, TestAssets.usdtTon.id, "USDT") } returns Result.success(null)

        assertNull(payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow())
    }

    @Test
    fun `methods that fail to load are a failed load, not a pair MoonPay does not sell`() = runTest {
        coEvery { api.paymentMethods("access", "eur") } returns Result.failure(MoonPayApiException.Http(500, null))

        assertTrue(payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").isFailure)
    }

    @Test
    fun `MoonPay with no method of its own for the fiat goes to the merchants`() = runTest {
        coEvery { api.paymentMethods("access", "eur") } returns Result.success(
            MoonPayPaymentMethods(configs = listOf(config("google_pay"), config("apple_pay")), stored = emptyList()),
        )

        assertNull(payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow())
    }

    @Test
    fun `a quote about to run out is paid with a new one even when it gives less, as on iOS`() = runTest {
        val less = quote(lifetime = 30).copy(destination = MoonPayMoney(amount = "48.9", asset = MoonPayAsset(code = "usdt_ton")))
        coEvery { api.buyQuote(any(), any()) } returns Result.success(less)

        val checkout = payments.checkout(available(card(), quote(lifetime = 2))).getOrThrow() as RampCheckout.Pay

        assertEquals(less, checkout.data.quote)
    }

    @Test
    fun `a quote handed to MoonPay is not paid again`() = runTest {
        val shown = quote(lifetime = 20)
        val fresh = quote(lifetime = 30)
        coEvery { api.buyQuote(any(), any()) } returns Result.success(fresh)
        payments.checkout(available(card(), shown)).getOrThrow()

        val again = payments.checkout(available(card(), shown)).getOrThrow() as RampCheckout.Pay

        assertTrue(again.data.quote === fresh)
        coVerify(exactly = 1) { api.buyQuote(any(), any()) }
    }

    @Test
    fun `no quote handed to MoonPay is paid again, after several payments too`() = runTest {
        val shown = quote(lifetime = 20)
        val fresh = listOf(quote(lifetime = 30), quote(lifetime = 40), quote(lifetime = 50))
        coEvery { api.buyQuote(any(), any()) } returnsMany fresh.map { Result.success(it) }
        payments.checkout(available(card(), shown)).getOrThrow()

        val paid = List(2) { (payments.checkout(available(card(), shown)).getOrThrow() as RampCheckout.Pay).data.quote }

        assertEquals(fresh.take(2), paid)
    }

    @Test
    fun `a P2P that fails to load only goes missing`() = runTest {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.usdtTon.id, "EUR") } throws IOException("offline")

        val methods = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals(listOf("card", "revolut_pay"), methods.filter { it.card == null }.map { it.id })
    }

    @Test
    fun `without our names a method is named as on iOS, others by their type`() = runTest {
        coEvery { exchangeRepository.getMoonPayPaymentMethods() } throws IOException("offline")

        val methods = payments.loadMethods(TestAssets.usdtTon.id, "USDT", "EUR").getOrThrow().orEmpty()

        assertEquals(
            listOf("string:${Localization.moonpay_payment_method_debit_card}", "Revolut Pay", "P2P Express"),
            methods.filter { it.card == null }.map { it.title },
        )
    }

    @Test
    fun `an offer is quoted for the wallet and the saved card, at the fiat's precision`() = runTest {
        val sent = slot<MoonPayBuyQuoteRequest>()
        coEvery { api.buyQuote("access", capture(sent)) } returns Result.success(quote(lifetime = 20))

        val offer = payments.requestOffer(request(card(), "50.123")).getOrThrow() as RampOffer.Available

        assertEquals(
            MoonPayBuyQuoteRequest(
                source = MoonPayMoney(amount = "50.13", asset = MoonPayAsset(code = "eur")),
                destination = MoonPayMoney(asset = MoonPayAsset(code = "usdt_ton")),
                wallet = MoonPayWalletReference(address = "UQwallet"),
                paymentMethod = MoonPayPaymentMethodReference(type = "card", id = "visa"),
            ),
            sent.captured,
        )
        assertEquals(BigDecimal("49"), offer.receive)
        assertEquals(0, BigDecimal("0.5").compareTo(offer.rate))
        assertEquals(20.seconds, offer.lifetime)
        assertNull(offer.badgeUrl)
    }

    @Test
    fun `an amount MoonPay rejects is out of its limits, rounded at the fiat's precision to pass`() = runTest {
        coEvery { api.buyQuote(any(), any()) } returns Result.failure(limit(min = "20.001"))
        assertEquals(
            RampOffer.OutOfLimits(null, RampLimitViolation.BelowMin(BigDecimal("20.01"))),
            payments.requestOffer(request(card(), "10")).getOrThrow(),
        )

        coEvery { api.buyQuote(any(), any()) } returns Result.failure(limit(max = "5000.009"))
        assertEquals(
            RampOffer.OutOfLimits(null, RampLimitViolation.AboveMax(BigDecimal("5000.00"))),
            payments.requestOffer(request(card(), "9000")).getOrThrow(),
        )
    }

    @Test
    fun `a failed quote is a failure`() = runTest {
        coEvery { api.buyQuote(any(), any()) } returns Result.failure(MoonPayApiException.Http(500, null))

        assertTrue(payments.requestOffer(request(card(), "50")).isFailure)
    }

    @Test
    fun `the suggestion is the minimum a quote for nothing is rejected with, and there is no max`() = runTest {
        coEvery { api.buyQuote(any(), match { it.source.amount == "0" }) } returns Result.failure(limit(min = "19.991"))

        assertEquals(RampAmountRange(BigDecimal("20.00"), max = null), payments.loadAmountRange(card(), "EUR").getOrThrow())
    }

    @Test
    fun `a quote for nothing that fails otherwise is a failure`() = runTest {
        coEvery { api.buyQuote(any(), any()) } returns Result.failure(MoonPayApiException.Http(500, null))

        assertTrue(payments.loadAmountRange(card(), "EUR").isFailure)
    }

    @Test
    fun `a fresh quote is paid as it is, in the status sheet`() = runTest {
        val quote = quote(lifetime = 20)

        val checkout = payments.checkout(available(card(), quote)).getOrThrow() as RampCheckout.Pay

        assertEquals(quote, checkout.data.quote)
        assertEquals("Debit card", checkout.data.paymentTitle)
        assertEquals(MoonPayTheme.Dark, checkout.data.theme)
        coVerify(exactly = 0) { api.buyQuote(any(), any()) }
    }

    @Test
    fun `a quote about to run out is asked for again before paying`() = runTest {
        val fresh = quote(lifetime = 30)
        coEvery { api.buyQuote(any(), any()) } returns Result.success(fresh)

        val checkout = payments.checkout(available(card(), quote(lifetime = 2))).getOrThrow() as RampCheckout.Pay

        assertEquals(fresh, checkout.data.quote)
    }

    @Test
    fun `a quote with a challenge opens it, then loads again`() = runTest {
        val challenged = quote(lifetime = 20).copy(executable = false, challenge = buildJsonObject { put("url", "https://platform.moonpay.com/c") })

        val checkout = async { payments.checkout(available(card(), challenged)).getOrThrow() }
        val entry = frames.state.first { it.entries.isNotEmpty() }.entries.single()
        assertEquals(MoonPayFrame.Challenge("https://platform.moonpay.com/c", "client", MoonPayTheme.Dark, resume = false), entry.frame)
        entry.onEvent(MoonPayFrameEvent.Cancelled(null))

        assertEquals(RampCheckout.Reload, checkout.await())
    }

    @Test
    fun `a challenge that fails is shown, and the screen loads again as after any challenge`() = runTest {
        val challenged = quote(lifetime = 20).copy(executable = false, challenge = buildJsonObject { put("url", "https://platform.moonpay.com/c") })

        val checkout = async { payments.checkout(available(card(), challenged)).getOrThrow() }
        frames.state.first { it.entries.isNotEmpty() }.entries.single()
            .onEvent(MoonPayFrameEvent.Error(MoonPayFrameError("code", "message")))

        assertEquals(RampCheckout.ReloadAfterFailure, checkout.await())
    }

    @Test
    fun `a challenge frame that fails to load is shown, and the screen loads again`() = runTest {
        val challenged = quote(lifetime = 20).copy(executable = false, challenge = buildJsonObject { put("url", "https://platform.moonpay.com/c") })

        val checkout = async { payments.checkout(available(card(), challenged)).getOrThrow() }
        frames.state.first { it.entries.isNotEmpty() }.entries.single()
            .onFailure(MoonPayFrameException.LoadFailed("offline"))

        assertEquals(RampCheckout.ReloadAfterFailure, checkout.await())
    }

    @Test
    fun `a new card is added first, and the screen learns which card it was`() = runTest {
        val checkout = async { payments.checkout(available(card(cardId = null), quote(lifetime = 20))).getOrThrow() }
        val entry = frames.state.first { it.entries.isNotEmpty() }.entries.single()
        assertEquals(MoonPayFrame.AddCard("client", MoonPayTheme.Dark), entry.frame)
        entry.onEvent(MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Card(MoonPaySavedCard(id = "new"))))

        assertEquals(RampCheckout.CardAdded("new"), checkout.await())
    }

    @Test
    fun `closing the new card is a cancel, not a failure`() = runTest {
        val checkout = async { payments.checkout(available(card(cardId = null), quote(lifetime = 20))).getOrThrow() }
        frames.state.first { it.entries.isNotEmpty() }.entries.single().onClose()

        assertEquals(RampCheckout.Cancelled, checkout.await())
    }

    private fun config(type: String, active: Boolean = true, types: List<String> = listOf("buy")): MoonPayPaymentMethodConfig {
        return MoonPayPaymentMethodConfig(
            type = type,
            capabilities = MoonPayPaymentMethodConfig.Capabilities(supportedTransactionTypes = types),
            availability = MoonPayPaymentMethodAvailability(active = active),
        )
    }

    private fun detail(vararg methods: RampMethod): RampAssetDetail {
        return RampAssetDetail(
            assetId = TestAssets.usdtTon.id,
            symbol = "USDT",
            decimals = 6,
            image = null,
            networkName = "TON",
            networkImage = null,
            methods = methods.toList(),
            extraIdRequired = false,
            extraIdName = null,
        )
    }

    private fun card(cardId: String? = "visa"): RampPaymentMethod {
        return RampPaymentMethod(
            id = "card",
            title = "Debit card",
            subtitle = null,
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.MoonPay(
                type = "card",
                assetId = TestAssets.usdtTon.id,
                destination = MoonPayAsset(code = "usdt_ton"),
                cardId = cardId,
                cardType = cardId?.let { "card" },
            ),
        )
    }

    private fun request(method: RampPaymentMethod, amount: String): RampQuoteRequest {
        return RampQuoteRequest(TestAssets.usdtTon.id, "EUR", method, BigDecimal(amount))
    }

    private fun quote(lifetime: Int): MoonPayBuyQuote {
        return MoonPayBuyQuote(
            source = MoonPayMoney(amount = "50", asset = MoonPayAsset(code = "eur")),
            destination = MoonPayMoney(amount = "49", asset = MoonPayAsset(code = "usdt_ton")),
            exchangeRate = "2",
            executable = true,
            signature = "signature",
            expiresAtMark = clock.markNow() + lifetime.seconds,
        )
    }

    private fun available(method: RampPaymentMethod, quote: MoonPayBuyQuote): RampOffer.Available {
        return RampOffer.Available(
            request = request(method, "50"),
            receive = BigDecimal("49"),
            rate = null,
            lifetime = quote.lifetime,
            badgeUrl = null,
            ticket = RampTicket.MoonPay(quote),
        )
    }

    private fun limit(min: String? = null, max: String? = null): MoonPayApiException.Http {
        return MoonPayApiException.Http(
            status = 400,
            body = MoonPayServerError(
                errors = listOf(
                    MoonPayErrorDetail(field = "source.amount", details = MoonPayErrorDetail.Details(minBuyAmount = min, maxBuyAmount = max)),
                ),
            ),
        )
    }
}
private suspend fun RampPayments.checkout(offer: RampOffer.Available): Result<RampCheckout> = checkout(offer.request.method, offer)

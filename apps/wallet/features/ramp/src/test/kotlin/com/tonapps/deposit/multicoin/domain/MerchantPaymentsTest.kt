package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampQuotes
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangeMerchantSlug.mercuryo
import io.exchangeapi.models.ExchangeMerchantSlug.transak
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.RampFees
import io.infrastructure.ClientException
import io.infrastructure.ServerException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class MerchantPaymentsTest {

    private val rampRepository = mockk<RampRepository>()
    private val exchangeRepository = mockk<ExchangeRepository> {
        coEvery { getMerchants(TestWallet.ID) } returns listOf(merchantInfo(mercuryo, "Mercuryo"))
    }
    private val mcAccountRepository = mockk<McAccountRepository> {
        coEvery { findAccount(TestWallet.ID, TestAssets.ton.id, any(), any()) } returns testAccount(TestAssets.ton)
    }
    private val payments = MerchantPayments(TestWallet.ID, rampRepository, exchangeRepository, RampWalletAddress(mcAccountRepository))

    @Test
    fun `an asset the onramp doesn't sell has no payment methods`() = runTest {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD") } throws ClientException("Not found", statusCode = 404)

        assertTrue(payments.loadMethods(TestAssets.ton.id, "TON", "USD").getOrThrow()?.isEmpty() == true)
    }

    @Test
    fun `a server error while loading payment methods is a failure`() = runTest {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD") } throws ServerException("Unavailable", statusCode = 503)

        assertTrue(payments.loadMethods(TestAssets.ton.id, "TON", "USD").isFailure)
    }

    @Test
    fun `the suggested amount is the first merchant's minimum and the max the highest maximum, at the fiat's precision`() = runTest {
        val method = method(
            ExchangePaymentMethodType.card,
            RampProvider(merchant = mercuryo, fiat = "USD", limits = ExchangeLimits(min = "25.001", max = "1000")),
            RampProvider(merchant = transak, fiat = "USD", limits = ExchangeLimits(min = "10", max = "29000.555")),
            RampProvider(merchant = transak, fiat = "EUR", limits = ExchangeLimits(min = "5", max = "99999")),
        )

        val range = payments.loadAmountRange(paymentMethod(method), "USD").getOrThrow()

        assertEquals(BigDecimal("25.01"), range.suggested)
        assertEquals(BigDecimal("29000.55"), range.max)
    }

    @Test
    fun `a merchant without a maximum leaves no max`() = runTest {
        val method = method(
            ExchangePaymentMethodType.card,
            RampProvider(merchant = mercuryo, fiat = "USD", limits = ExchangeLimits(min = "25", max = "300")),
            RampProvider(merchant = transak, fiat = "USD", limits = ExchangeLimits(min = "10")),
        )

        val range = payments.loadAmountRange(paymentMethod(method), "USD").getOrThrow()

        assertNull(range.max)
    }

    @Test
    fun `a method without limits has no suggestion and no max`() = runTest {
        val range = payments.loadAmountRange(paymentMethod(method(ExchangePaymentMethodType.card, provider(mercuryo))), "USD").getOrThrow()

        assertNull(range.suggested)
        assertNull(range.max)
    }

    @Test
    fun `payment methods are those with a provider for the fiat, P2P last`() = runTest {
        assetDetail(
            method(ExchangePaymentMethodType.p2p, provider(mercuryo)),
            method(ExchangePaymentMethodType.card, provider(mercuryo)),
            method(ExchangePaymentMethodType.sepa, provider(mercuryo, fiat = "EUR")),
            method(ExchangePaymentMethodType.google_pay, provider(transak)),
        )

        val methods = payments.loadMethods(TestAssets.ton.id, "TON", "USD").getOrThrow()

        assertEquals(listOf("card", "google_pay", "p2p"), methods?.map { it.id })
    }

    @Test
    fun `an amount below every provider's minimum is out of limits without a quote`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo, min = "30"), provider(transak, min = "20"))

        val offer = payments.requestOffer(request(card, "10")).getOrThrow()

        assertEquals(
            RampOffer.OutOfLimits(null, RampLimitViolation.BelowMin(BigDecimal("20"))),
            offer,
        )
        coVerify(exactly = 0) { rampRepository.getQuotes(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the offer is the best merchant's quote`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(transak, min = "60"), provider(mercuryo))
        quotes(quote(transak, receive = "49", rate = "0.98"), quote(mercuryo, receive = "48", rate = "0.96"))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertEquals("https://img/mercuryo", offer.badgeUrl)
        assertEquals(BigDecimal("48"), offer.receive)
        assertEquals(BigDecimal("0.96"), offer.rate)
        assertEquals(RampTicket.Merchant(mercuryo, "tx-mercuryo", "Mercuryo"), offer.ticket)
    }

    @Test
    fun `merchants that fail to load leave the offer with the slug as its name, asked once`() = runTest {
        coEvery { exchangeRepository.getMerchants(TestWallet.ID) } throws IOException("offline")
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo, receive = "48"))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertNull(offer.badgeUrl)
        assertEquals(RampTicket.Merchant(mercuryo, "tx-mercuryo", "mercuryo"), offer.ticket)
        coVerify(exactly = 1) { exchangeRepository.getMerchants(TestWallet.ID) }
    }

    @Test
    fun `a quote that gives nothing loses to the next merchant`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(transak), provider(mercuryo))
        quotes(quote(transak, receive = "0"), quote(mercuryo, receive = "48"))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertEquals(RampTicket.Merchant(mercuryo, "tx-mercuryo", "Mercuryo"), offer.ticket)
    }

    @Test
    fun `a merchant that takes the amount but sent no quote is a failure, not a limit`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo), provider(transak, min = "30"))
        coEvery { rampRepository.getQuotes(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD", any(), any(), any(), any()) } returns
            RampQuotes(items = listOf(quote(transak, min = "30")), suggested = listOf(quote(mercuryo)), unavailableReason = null)

        assertTrue(payments.requestOffer(request(card, "20")).isFailure)
    }

    @Test
    fun `no amount is not quoted`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))

        assertTrue(payments.requestOffer(request(card, "0")).isFailure)
        coVerify(exactly = 0) { rampRepository.getQuotes(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the amount is quoted and ordered at the fiat's precision`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo))

        val offer = payments.requestOffer(request(card, "10.123")).getOrThrow() as RampOffer.Available

        assertEquals(BigDecimal("10.13"), offer.request.amount)
        coVerify { rampRepository.getQuotes(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD", "10.13", ExchangePaymentMethodType.card, null, null) }
    }

    @Test
    fun `without a server rate it comes from the amounts`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo, paid = "50", receive = "45", rate = "0"))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertEquals(0, BigDecimal("0.9").compareTo(offer.rate))
    }

    @Test
    fun `a quote's own minimum puts the amount out of limits`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo, min = "100"))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow()

        assertEquals(
            RampOffer.OutOfLimits("https://img/mercuryo", RampLimitViolation.BelowMin(BigDecimal("100"))),
            offer,
        )
    }

    @Test
    fun `a merchant without its card still quotes`() = runTest {
        coEvery { exchangeRepository.getMerchants(TestWallet.ID) } throws IOException("offline")
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo))

        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertNull(offer.badgeUrl)
    }

    @Test
    fun `a failed quote request is a failed result`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        coEvery { rampRepository.getQuotes(any(), any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")

        assertTrue(payments.requestOffer(request(card, "50")).exceptionOrNull() is IOException)
    }

    @Test
    fun `checkout orders the offer to the wallet's address`() = runTest {
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo))
        coEvery {
            rampRepository.createOrder(
                TestWallet.ID,
                RampType.RampOn,
                TestAssets.ton.id,
                "USD",
                "50.00",
                TestWallet.addressOf(TestAssets.ton),
                ExchangePaymentMethodType.card,
                mercuryo,
                "tx-mercuryo",
                null,
            )
        } returns "https://widget"
        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertEquals(RampCheckout.OpenUrl("https://widget"), payments.checkout(offer).getOrThrow())
    }

    @Test
    fun `checkout without an account for the asset fails`() = runTest {
        coEvery { mcAccountRepository.findAccount(any(), any(), any(), any()) } returns null
        val card = method(ExchangePaymentMethodType.card, provider(mercuryo))
        quotes(quote(mercuryo))
        val offer = payments.requestOffer(request(card, "50")).getOrThrow() as RampOffer.Available

        assertTrue(payments.checkout(offer).isFailure)
    }

    private fun assetDetail(vararg methods: RampMethod) {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD") } returns detail(*methods)
    }

    private fun detail(vararg methods: RampMethod): RampAssetDetail {
        return RampAssetDetail(
            assetId = TestAssets.ton.id,
            symbol = "TON",
            decimals = 9,
            image = null,
            networkName = null,
            networkImage = null,
            methods = methods.toList(),
            extraIdRequired = false,
            extraIdName = null,
        )
    }

    private fun quotes(vararg items: RampQuote) {
        coEvery { rampRepository.getQuotes(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD", any(), any(), any(), any()) } returns
            RampQuotes(items = items.toList(), suggested = emptyList(), unavailableReason = null)
    }

    private fun request(method: RampMethod, amount: String): RampQuoteRequest {
        return RampQuoteRequest(TestAssets.ton.id, "USD", paymentMethod(method), BigDecimal(amount))
    }

    private fun paymentMethod(method: RampMethod): RampPaymentMethod {
        return RampPaymentMethod(
            id = method.type.value,
            title = method.name,
            subtitle = null,
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.Merchant(method),
        )
    }

    private fun method(type: ExchangePaymentMethodType, vararg providers: RampProvider): RampMethod {
        return RampMethod(type = type, name = type.value, image = "", providers = providers.toList())
    }

    private fun provider(merchant: ExchangeMerchantSlug, min: String? = null, fiat: String = "USD"): RampProvider {
        return RampProvider(merchant = merchant, fiat = fiat, limits = ExchangeLimits(min = min))
    }

    private fun quote(
        merchant: ExchangeMerchantSlug,
        paid: String = "50",
        receive: String = "49",
        rate: String = "0.98",
        min: String? = null,
    ): RampQuote {
        return RampQuote(
            merchant = merchant,
            paymentMethod = ExchangePaymentMethodType.card,
            amountIn = paid,
            amountOut = receive,
            rate = rate,
            fees = RampFees(total = "1"),
            merchantTransactionId = "tx-$merchant",
            minAmount = min,
            maxAmount = null,
        )
    }

    private fun merchantInfo(slug: ExchangeMerchantSlug, title: String): ExchangeMerchantInfo {
        return ExchangeMerchantInfo(
            id = slug.value,
            title = title,
            description = "",
            image = "https://img/${slug.value}",
            fee = "",
            buttons = emptyList(),
        )
    }
}

private suspend fun RampPayments.checkout(offer: RampOffer.Available): Result<RampCheckout> = checkout(offer.request.method, offer)

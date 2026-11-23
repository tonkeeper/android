package com.tonapps.deposit.multicoin.screens.ramp.amount

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampQuotes
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.utils.AmountError
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.deposit.testing.PlatformNumberFormat
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.RampFees
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal

class RampAmountFeatureTest : BaseUnitTest() {

    private val rampRepository = mockk<RampRepository>()
    private val exchangeRepository = mockk<ExchangeRepository>()
    private val mcAccountRepository = mockk<McAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { currency } returns mockk { every { code } returns "USD" }
    }

    private val quotedAmounts = mutableListOf<String>()
    private var quotesIgnoreMinimum = false
    private var quoteMaximums = mapOf<ExchangeMerchantSlug, String>()
    private var merchantsWithoutQuotes = setOf<ExchangeMerchantSlug>()
    private var layoutLimits = mapOf(
        ExchangeMerchantSlug.mercuryo to "30",
        ExchangeMerchantSlug.moonpay to "25",
    )

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        coEvery { mcAccountRepository.findAccount(TestWallet.ID, TestAssets.ton.id, any(), any()) } returns testAccount(TestAssets.ton)
        coEvery { exchangeRepository.getMerchants(TestWallet.ID) } returns emptyList()
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD") } answers {
            RampAssetDetail(
                assetId = TestAssets.ton.id,
                symbol = "TON",
                decimals = 9,
                image = null,
                networkName = null,
                networkImage = null,
                methods = listOf(
                    RampMethod(
                        type = ExchangePaymentMethodType.card,
                        name = "Card",
                        image = "",
                        providers = layoutLimits.map { (merchant, min) ->
                            RampProvider(merchant, "USD", ExchangeLimits(min = min))
                        },
                    )
                ),
                extraIdRequired = false,
                extraIdName = null,
            )
        }
        coEvery { rampRepository.getQuotes(TestWallet.ID, RampType.RampOn, TestAssets.ton.id, "USD", any(), any(), any(), any()) } coAnswers {
            val amount = arg<String>(4)
            quotedAmounts += amount
            delay(QUOTE_MS)
            quotesFor(BigDecimal(amount))
        }
        coEvery { rampRepository.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(QUOTE_MS)
            WIDGET_URL
        }
    }

    @Test
    fun theOpeningAmountIsTheLargestMinimumRoundedUpToAWholeFiat() {
        layoutLimits = mapOf(
            ExchangeMerchantSlug.mercuryo to "29.01",
            ExchangeMerchantSlug.moonpay to "24.3",
        )

        val state = open().data()

        assertEquals("30", state.initialAmount)
    }

    @Test
    fun anAmountBelowEveryMinimumKeepsTheClosestProviderWithItsMinimum() {
        val feature = open()

        enter(feature, "10")

        val state = feature.data()
        assertEquals("moonpay", state.selectedProviderId)
        assertTrue(state.selectedProvider!!.info.isBest)
        assertNull(state.selectedProvider!!.quote)
        assertEquals(AmountError.BelowMin(Coins.of("25")), state.amountError)
        assertFalse(state.canContinue)
    }

    @Test
    fun aQuoteBelowItsOwnMinimumDoesNotServeTheAmount() {
        quotesIgnoreMinimum = true
        val feature = open()

        enter(feature, "24.99")

        val state = feature.data()
        assertEquals("moonpay", state.selectedProviderId)
        assertNull(state.selectedProvider!!.quote)
        assertEquals(AmountError.BelowMin(Coins.of("25")), state.amountError)
        assertFalse(state.canContinue)
    }

    @Test
    fun anAmountOneProviderServesSelectsItWithoutAMinimum() {
        val feature = open()

        enter(feature, "27")

        val state = feature.data()
        assertEquals("moonpay", state.selectedProviderId)
        assertNull(state.amountError)
        assertTrue(state.canContinue)
        assertTrue(state.isQuotedFor(Coins.of("27")))
    }

    @Test
    fun pickingAProviderBelowItsMinimumFillsTheMinimumRoundedUpAndKeepsThePick() {
        layoutLimits = mapOf(
            ExchangeMerchantSlug.mercuryo to "29.4",
            ExchangeMerchantSlug.moonpay to "25",
        )
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "10")

        feature.selectProvider("mercuryo", Coins.of("29.4"))
        settle()

        assertEquals(RampAmountEvent.UpdateAmount("30"), events.last())
        assertEquals("30", quotedAmounts.last())
        val state = feature.data()
        assertEquals("mercuryo", state.selectedProviderId)
        assertNull(state.amountError)
    }

    @Test
    fun pickingAProviderAboveItsMaximumFillsTheMaximumRoundedDown() {
        quoteMaximums = mapOf(ExchangeMerchantSlug.mercuryo to "16000.5")
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "20000")

        feature.selectProvider("mercuryo", Coins.of("30"))
        settle()

        assertEquals(RampAmountEvent.UpdateAmount("16000"), events.last())
        assertEquals("mercuryo", feature.data().selectedProviderId)
    }

    @Test
    fun pickingAProviderWithNeitherQuoteNorLimitsKeepsTheShownProvider() {
        merchantsWithoutQuotes = setOf(ExchangeMerchantSlug.mercuryo)
        val feature = open()
        enter(feature, "10")

        feature.selectProvider("mercuryo", null)
        settle()

        val state = feature.data()
        assertEquals("moonpay", state.selectedProviderId)
        assertEquals(AmountError.BelowMin(Coins.of("25")), state.amountError)
    }

    @Test
    fun togglingToCryptoAndBackRestoresTheTypedFiatAndPricesTheSameFiat() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "30")

        feature.toggleInputCurrency("30")
        settle()

        assertEquals(RampAmountEvent.UpdateAmount("25.69"), events.last())
        val crypto = feature.data()
        assertTrue(crypto.isCryptoInput)
        assertEquals("TON", crypto.inputCurrencyCode)
        assertEquals("30", quotedAmounts.last())
        assertTrue(crypto.isQuotedFor(Coins.of("25.69")))

        feature.toggleInputCurrency("25.69")
        settle()

        assertEquals(RampAmountEvent.UpdateAmount("30"), events.last())
        assertFalse(feature.data().isCryptoInput)
        assertEquals("30", quotedAmounts.last())
    }

    @Test
    fun aTypedCryptoAmountIsPricedInFiatAtTheLastRate() {
        val feature = open()
        enter(feature, "30")
        feature.toggleInputCurrency("30")
        settle()

        enter(feature, "10")

        assertEquals("11.67", quotedAmounts.last())
        assertEquals(AmountError.BelowMin(Coins.of("25")), feature.data().amountError)

        enter(feature, "20")

        assertEquals("23.35", quotedAmounts.last())
    }

    @Test
    fun repeatedContinueTapsCreateOneOrder() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "40")

        repeat(3) { feature.continueWithProvider() }
        scheduler.runCurrent()
        repeat(3) { feature.continueWithProvider() }
        settle()
        feature.continueWithProvider()
        settle()

        coVerify(exactly = 1) { rampRepository.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(listOf<RampAmountEvent>(RampAmountEvent.Continue(WIDGET_URL)), events)
        assertTrue(feature.data().isContinuing)
    }

    @Test
    fun anOrderNobodyIsLeftToOpenLetsContinueAgain() {
        val feature = open()
        enter(feature, "40")

        feature.continueWithProvider()
        settle()

        coVerify(exactly = 1) { rampRepository.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(feature.data().isContinuing)
        assertTrue(feature.data().canContinue)
    }

    @Test
    fun aFailedOrderLetsContinueAgain() {
        coEvery { rampRepository.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(QUOTE_MS)
            throw IllegalStateException("provider is down")
        }
        val feature = open()
        enter(feature, "40")

        feature.continueWithProvider()
        settle()

        assertFalse(feature.data().isContinuing)
        assertTrue(feature.data().canContinue)
    }

    private fun quotesFor(amount: BigDecimal): RampQuotes {
        val serving = layoutLimits.filter { (merchant, min) ->
            (quotesIgnoreMinimum || BigDecimal(min) <= amount) && amount <= BigDecimal(maximumOf(merchant))
        }.keys
        val quotes = (layoutLimits.keys - merchantsWithoutQuotes).map { merchant ->
            val rate = if (merchant in serving) { RATE } else { SUGGESTED_RATE }
            RampQuote(
                merchant = merchant,
                paymentMethod = ExchangePaymentMethodType.card,
                amountIn = amount.toPlainString(),
                amountOut = amount.multiply(rate).toPlainString(),
                rate = rate.toPlainString(),
                fees = RampFees(total = "0"),
                merchantTransactionId = "tx-${merchant.value}",
                minAmount = layoutLimits.getValue(merchant),
                maxAmount = maximumOf(merchant),
            )
        }
        return RampQuotes(
            items = quotes.filter { it.merchant in serving }.sortedBy { it.merchant != ExchangeMerchantSlug.moonpay },
            suggested = quotes.filter { it.merchant !in serving },
            unavailableReason = null,
        )
    }

    private fun maximumOf(merchant: ExchangeMerchantSlug): String {
        return quoteMaximums[merchant] ?: "29000"
    }

    private fun open(): RampAmountFeature {
        val feature = RampAmountFeature(
            data = RampAmountData(
                walletId = TestWallet.ID,
                rampType = RampType.RampOn,
                assetId = TestAssets.ton.id,
                paymentMethodType = ExchangePaymentMethodType.card.value,
                analyticsFrom = DepositFlowFrom.WalletScreen,
                fiat = "USD",
            ),
            rampRepository = rampRepository,
            exchangeRepository = exchangeRepository,
            mcAccountRepository = mcAccountRepository,
            settingsRepository = settingsRepository,
        )
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: RampAmountFeature, amount: String) {
        feature.onAmountInput(amount)
        settle()
    }

    private fun settle() {
        scheduler.advanceTimeBy(DEBOUNCE_MS + QUOTE_MS + 1)
        scheduler.runCurrent()
    }

    private fun RampAmountFeature.data(): RampAmountState.Data {
        return state.value as RampAmountState.Data
    }

    private fun collectEvents(feature: RampAmountFeature): List<RampAmountEvent> {
        val events = mutableListOf<RampAmountEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    companion object {
        private const val QUOTE_MS = 1_000L
        private const val DEBOUNCE_MS = 300L
        private const val WIDGET_URL = "https://buy.moonpay.com/widget"
        private val RATE = BigDecimal("0.8566")
        private val SUGGESTED_RATE = BigDecimal("0.8")

        @JvmStatic
        @BeforeClass
        fun stubPlatformNumberFormat() {
            PlatformNumberFormat.stub()
        }
    }
}

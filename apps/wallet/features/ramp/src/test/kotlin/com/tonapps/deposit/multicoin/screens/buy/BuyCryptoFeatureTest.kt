package com.tonapps.deposit.multicoin.screens.buy

import com.tonapps.wallet.testing.TestWallet
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCommand
import com.tonapps.deposit.multicoin.moonpay.domain.isFinal
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayButtonPayments
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentInteractor
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentFailure
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentState
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.SupervisorJob
import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import com.tonapps.deposit.multicoin.analytics.BuyCryptoPurchase
import com.tonapps.deposit.multicoin.analytics.BuyCryptoReport
import androidx.lifecycle.SavedStateHandle
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowProviderMode
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.MoonPayRamp
import com.tonapps.deposit.multicoin.domain.MoonPaySignIn
import com.tonapps.deposit.multicoin.domain.RampAmountRange
import com.tonapps.deposit.multicoin.domain.RampCheckout
import com.tonapps.deposit.multicoin.domain.RampLimitViolation
import com.tonapps.deposit.multicoin.domain.RampOffer
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampQuoteRequest
import com.tonapps.deposit.multicoin.domain.RampSource
import com.tonapps.deposit.multicoin.domain.RampTicket
import com.tonapps.deposit.testing.PlatformNumberFormat
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.deposit.data.ExchangeRepository
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.MoonPayPaymentMethod
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import com.tonapps.deposit.multicoin.domain.RampSavedCard
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentFeeKind
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentRetry
import com.tonapps.network.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BuyCryptoFeatureTest : BaseUnitTest() {

    private val card = method(ExchangePaymentMethodType.card)
    private val p2p = method(ExchangePaymentMethodType.p2p)
    private val asset = RampAssetDetail(
        assetId = TestAssets.usdtTon.id,
        symbol = "USDT",
        decimals = 6,
        image = "https://img/usdt",
        networkName = "TON",
        networkImage = null,
        methods = listOf(rampMethod(ExchangePaymentMethodType.card)),
        extraIdRequired = false,
        extraIdName = null,
    )
    private val badge = "https://img/mercuryo"
    private val ticket = RampTicket.Merchant(ExchangeMerchantSlug.mercuryo, "tx", "Mercuryo")

    private val settingsRepository = mockk<SettingsRepository> {
        every { currency } returns WalletCurrency.USD
    }
    private val source = mockk<RampSource> {
        coEvery { findDefaultAsset(any()) } returns Result.success(RampAsset(TestAssets.usdtTon, "TON", listOf("USD")))
        coEvery { loadAssetDetail(TestAssets.usdtTon.id) } returns Result.success(asset)
        coEvery { resolveFiat(asset, any()) } answers { secondArg() }
        coEvery { createP2PSessionUrl(any(), any()) } returns Result.success("https://p2p")
        coEvery { appAssetDetail(any()) } returns Result.success(null)
    }
    private val exchange = mockk<ExchangeRepository> {
        coEvery { getMerchants(TestWallet.ID) } returns listOf(merchant(ExchangeMerchantSlug.moonpay), merchant(ExchangeMerchantSlug.mercuryo), merchant(ExchangeMerchantSlug.transak))
        coEvery { getMoonPayPaymentMethods() } returns listOf(MoonPayPaymentMethod(type = "card", title = "Debit card", subtitle = "Instant"))
    }

    private val payments = mockk<RampPayments> {
        coEvery { loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(card, p2p))
        coEvery { requestOffer(any()) } answers { Result.success(available(firstArg())) }
        coEvery { loadAmountRange(any(), any()) } returns Result.success(RampAmountRange(suggested = null, max = null))
        coEvery { checkout(any(), any()) } returns Result.success(RampCheckout.OpenUrl("https://widget"))
    }

    private val moonPayPayments = mockk<RampPayments> {
        coEvery { loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayMethod("card")))
        coEvery { requestOffer(any()) } answers {
            Result.success((available(firstArg()) as RampOffer.Available).copy(badgeUrl = "https://img/moonpay"))
        }
        coEvery { loadAmountRange(any(), any()) } returns Result.success(RampAmountRange(suggested = null, max = null))
    }

    private val foreground = MutableStateFlow(true)
    private val online = MutableStateFlow(true)
    private val networkMonitor = mockk<NetworkMonitor> {
        every { isOnlineFlow } returns online
    }
    private val appLifecycle = mockk<AppLifecycleProvider> {
        every { observeWithoutGrace } returns foreground
    }

    private val scheduler get() = dispatchers.scheduler

    private val tracked = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun recordAnalytics() {
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        val helper = mockk<AnalyticsHelper> {
            every { events } returns DefaultEvents(recording)
            every { eventsTaggedWith(any()) } returns DefaultEvents(recording)
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns helper
    }

    @After
    fun stopRecordingAnalytics() {
        unmockkObject(AnalyticsHelper.Companion)
    }

    @Test
    fun `MAX stops at the field's billion cap`() {
        coEvery { payments.loadAmountRange(any(), any()) } returns Result.success(RampAmountRange(suggested = null, max = BigDecimal("2000000000")))

        val feature = open()

        assertEquals("1000000000", feature.quickAmounts.value?.max)
    }

    @Test
    fun `opening reports the start once and the amount screen at its first offer, not again after a restore`() {
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        enter(feature, "50")
        settle()
        enter(feature, "60")
        settle()
        open(handle = handle)
        settle()

        assertEquals(
            listOf(
                mapOf(
                    "from" to "wallet_screen",
                    "wallet_mode" to "multi",
                    "deposit_screen" to "buy_crypto",
                    "available_options" to "buy_with_fiat",
                ),
            ),
            sent("deposit_started"),
        )
        assertEquals(
            listOf(
                mapOf(
                    "from" to "wallet_screen",
                    "add_funds_option" to "buy_with_fiat",
                    "deposit_screen" to "buy_crypto",
                    "buy_asset" to TestAssets.usdtTon.id,
                    "sell_asset" to "USD",
                    "payment_method" to "card",
                    "provider_name" to "Mercuryo",
                    "provider_mode" to "merchant",
                ),
            ),
            sent("deposit_view_ramp_insert_amount"),
        )
    }

    @Test
    fun `continue to a merchant reports the tap and the widget with the merchant's transaction`() {
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        val purchase = mapOf(
            "from" to "wallet_screen",
            "add_funds_option" to "buy_with_fiat",
            "deposit_screen" to "buy_crypto",
            "buy_asset" to TestAssets.usdtTon.id,
            "sell_asset" to "USD",
            "payment_method" to "card",
            "provider_name" to "Mercuryo",
            "provider_mode" to "merchant",
        )
        assertEquals(listOf(purchase), sent("deposit_click_ramp_insert_amount_continue"))
        assertEquals(listOf(purchase + mapOf("wallet_mode" to "multi", "tx_id" to "tx")), sent("deposit_continue_to_ramp_provider"))
    }

    @Test
    fun `every deposit event of Buy crypto carries deposit_screen buy_crypto`() {
        val feature = open()
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        val depositEvents = tracked.filter { it.first.startsWith("deposit_") }
        assertTrue(depositEvents.isNotEmpty())
        depositEvents.forEach { (_, props) ->
            assertEquals("buy_crypto", props["deposit_screen"])
        }
    }

    @Test
    fun `MoonPay's amount screen is reported once its methods load, before any offer`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        coEvery { moonPayPayments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        val feature = open(moonPay = moonPay())
        assertEquals(listOf("deposit_started", "deposit_view_ramp_insert_amount"), tracked.map { it.first })

        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(
            listOf("deposit_started", "deposit_view_ramp_insert_amount", "deposit_click_ramp_insert_amount_continue"),
            tracked.map { it.first },
        )
        assertEquals("MoonPay", sent("deposit_view_ramp_insert_amount").single()["provider_name"])
    }

    @Test
    fun `a failed continue reports the tap only`() {
        coEvery { payments.checkout(any(), any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(1, sent("deposit_click_ramp_insert_amount_continue").size)
        assertTrue(sent("deposit_continue_to_ramp_provider").isEmpty())
    }

    @Test
    fun `P2P reports the market, not a ramp provider`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(listOf(p2p))
        val feature = open()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(
            listOf(
                mapOf(
                    "from" to "wallet_screen",
                    "wallet_mode" to "multi",
                    "add_funds_option" to "buy_with_fiat",
                    "deposit_screen" to "buy_crypto",
                ),
            ),
            sent("deposit_continue_to_p2p_market"),
        )
        assertTrue(sent("deposit_click_ramp_insert_amount_continue").isEmpty())
        assertTrue(sent("deposit_continue_to_ramp_provider").isEmpty())
    }

    @Test
    fun `a MoonPay payment reports MoonPay with the payment's own transaction id`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Pay(data))
        coEvery { moonPayPayments.requestOffer(any()) } answers {
            Result.success((available(firstArg()) as RampOffer.Available).copy(ticket = RampTicket.MoonPay(moonPayQuote())))
        }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        val sent = sent("deposit_continue_to_ramp_provider").single()
        assertEquals("MoonPay", sent["provider_name"])
        assertEquals("headless", sent["provider_mode"])
        assertEquals("card", sent["payment_method"])
        assertEquals("order", sent["tx_id"])
    }

    @Test
    fun `the screen opens on the default asset in the card's fiat with the first method`() {
        val feature = open(preferredCurrency = "EUR")

        assertFalse(feature.loading.value)
        assertEquals("EUR", feature.fiat.value)
        assertEquals(BuyCryptoReceive(amount = null, muted = false, asset = "USDT (TON)", iconUrl = "https://img/usdt", assetId = TestAssets.usdtTon.id), feature.receive.value)
        assertEquals(BuyCryptoPaymentRow.Method(BuyCryptoPayment("card", null, null, null, card = null), loading = false), feature.payment.value)
        coVerify { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") }
    }

    @Test
    fun `without a default asset in the preferred fiat the USD one is taken`() {
        coEvery { source.findDefaultAsset("EUR") } returns Result.success(null)

        val feature = open(preferredCurrency = "EUR")

        assertEquals("EUR", feature.fiat.value)
        coVerify { source.findDefaultAsset(WalletCurrency.USD.code) }
    }

    @Test
    fun `a passed asset is used as is`() {
        open(assetId = TestAssets.usdtTon.id)

        coVerify(exactly = 0) { source.findDefaultAsset(any()) }
    }

    @Test
    fun `an amount is quoted once typing settles and the offer is shown`() {
        val feature = open()

        enter(feature, "5")
        enter(feature, "50")
        assertTrue(feature.quoting.value)
        assertFalse(feature.canContinue.value)
        settle()

        assertEquals("49", feature.receive.value?.amount?.filter { it.isDigit() })
        assertEquals(BuyCryptoBadge.Image("https://img/mercuryo"), (feature.payment.value as? BuyCryptoPaymentRow.Method)?.payment?.badge)
        assertTrue(feature.canContinue.value)
        coVerify(exactly = 1) { payments.requestOffer(any()) }
        coVerify { payments.requestOffer(match { it.amount.compareTo(BigDecimal("50")) == 0 && it.fiat == "USD" && it.method == card }) }
    }

    @Test
    fun `typed text is trimmed to an amount at the fiat's precision`() {
        val feature = open()

        enter(feature, "12.345")
        assertEquals("12.34", feature.amount.value.text)
        enter(feature, "1a2")
        assertEquals("12", feature.amount.value.text)
        enter(feature, ".5")
        assertEquals("0.5", feature.amount.value.text)
        enter(feature, "12345678901234567")
        assertEquals("0.5", feature.amount.value.text)
    }

    @Test
    fun `the same amount written longer is not quoted again`() {
        val feature = open()

        enter(feature, "50")
        settle()
        enter(feature, "50.0")
        settle()

        coVerify(exactly = 1) { payments.requestOffer(any()) }
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `the merchant badge stays while a new quote loads`() {
        val feature = open()
        enter(feature, "50")
        settle()

        enter(feature, "60")

        assertTrue(feature.quoting.value)
        assertEquals(BuyCryptoBadge.Image("https://img/mercuryo"), (feature.payment.value as? BuyCryptoPaymentRow.Method)?.payment?.badge)
    }

    @Test
    fun `a recreated screen keeps the typed amount and quotes it`() {
        val handle = SavedStateHandle()
        enter(open(handle = handle), "75")

        val recreated = open(handle = handle)

        assertEquals("75", recreated.amount.value.text)
        assertTrue(recreated.canContinue.value)
    }

    @Test
    fun `an amount out of limits cannot continue, buys nothing and marks the amount`() {
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("20"))),
        )
        val feature = open()

        enter(feature, "10")
        settle()

        assertFalse(feature.canContinue.value)
        assertEquals("0", feature.receive.value?.amount)
        assertTrue(feature.receive.value!!.muted)
        assertTrue(feature.amountField.value!!.outOfLimits)
    }

    @Test
    fun `continue opens the merchant's widget for the offer`() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenUrl("https://widget")), events)
    }

    @Test
    fun `P2P needs no amount and opens the market`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(listOf(p2p))
        val feature = open()
        val events = collectEvents(feature)

        assertTrue(feature.canContinue.value)
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenUrl("https://p2p")), events)
        coVerify(exactly = 0) { payments.requestOffer(any()) }
    }

    @Test
    fun `a failed load offers a retry that loads again`() {
        coEvery { source.loadAssetDetail(any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        val events = collectEvents(feature)

        assertFalse(feature.loading.value)
        assertTrue(feature.failed.value)
        assertFalse(feature.canContinue.value)
        assertTrue(events.isEmpty())

        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } returns Result.success(asset)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertFalse(feature.failed.value)
        assertEquals(BuyCryptoPaymentRow.Method(BuyCryptoPayment("card", null, null, null, card = null), loading = false), feature.payment.value)
    }

    @Test
    fun `a retry that fails again shows the failure again`() {
        coEvery { source.loadAssetDetail(any()) } returns Result.failure(IOException("offline"))
        val feature = open()

        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertTrue(feature.failed.value)
        assertFalse(feature.loading.value)
        coVerify(exactly = 2) { source.loadAssetDetail(any()) }
    }

    @Test
    fun `a passed asset neither the onramp nor the app knows shows the stub without an asset`() {
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(null)

        val feature = open(assetId = TestAssets.ton.id)

        assertFalse(feature.failed.value)
        assertTrue(feature.nothingToBuy.value != null)
        assertNull(feature.receive.value?.asset)
        coVerify(exactly = 0) { source.findDefaultAsset(any()) }
    }

    @Test
    fun `a default without methods gives way to the USD default`() {
        val usdDefault = asset.copy(assetId = TestAssets.ton.id)
        coEvery { source.findDefaultAsset("EUR") } returns Result.success(RampAsset(TestAssets.usdtTon, "TON", listOf("EUR")))
        coEvery { source.findDefaultAsset("USD") } returns Result.success(RampAsset(TestAssets.ton, "TON", listOf("USD")))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(emptyList())
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(usdDefault)
        coEvery { source.resolveFiat(usdDefault, any()) } answers { secondArg() }
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(card))

        val feature = open(preferredCurrency = "EUR")

        assertNull(feature.nothingToBuy.value)
        assertEquals(BuyCryptoPaymentRow.Method(BuyCryptoPayment("card", null, null, null, card = null), loading = false), feature.payment.value)
        coVerify { payments.loadMethods(TestAssets.ton.id, any(), "EUR") }
    }

    @Test
    fun `an opened widget doesn't let continue order again`() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertFalse(feature.canContinue.value)
        assertEquals(false, feature.amountField.value?.editable)
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenUrl("https://widget")), events)
        coVerify(exactly = 1) { payments.checkout(any(), any()) }
    }

    @Test
    fun `a failed continue can be tried again`() {
        coEvery { payments.checkout(any(), any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertTrue(feature.canContinue.value)
        assertEquals(true, feature.amountField.value?.editable)
    }

    @Test
    fun `a passed asset without methods in the fiat shows its stub, not another asset`() {
        val other = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(other)
        coEvery { source.resolveFiat(other, any()) } answers { secondArg() }
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(emptyList())

        val feature = open(assetId = TestAssets.ton.id)

        assertTrue(feature.nothingToBuy.value.orEmpty().contains("USD"))
        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)
        coVerify(exactly = 0) { source.findDefaultAsset(any()) }
    }

    @Test
    fun `a passed asset the onramp doesn't know takes the app's description and shows its stub`() {
        val known = asset.copy(assetId = TestAssets.ton.id, symbol = "TON", methods = emptyList())
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(null)
        coEvery { source.appAssetDetail(TestAssets.ton.id) } returns Result.success(known)
        coEvery { source.resolveFiat(known, any()) } answers { secondArg() }
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(null)

        val feature = open(assetId = TestAssets.ton.id)

        assertFalse(feature.failed.value)
        assertTrue(feature.nothingToBuy.value != null)
        assertEquals("TON", feature.receive.value?.asset)
        coVerify(exactly = 0) { source.findDefaultAsset(any()) }
    }

    @Test
    fun `a passed asset the onramp doesn't know is bought through MoonPay where it sells it`() {
        val known = asset.copy(assetId = TestAssets.ton.id, symbol = "TON", methods = emptyList())
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(null)
        coEvery { source.appAssetDetail(TestAssets.ton.id) } returns Result.success(known)
        coEvery { source.resolveFiat(known, any()) } answers { secondArg() }
        coEvery { moonPayPayments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(moonPayMethod("card")))

        val feature = open(assetId = TestAssets.ton.id, moonPay = moonPay())

        assertNull(feature.nothingToBuy.value)
        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)
        assertTrue(feature.payment.value != null)
    }

    @Test
    fun `a reload after a card frame checks MoonPay inside its load, with Continue off`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Reload)
        // The screen's open checks at once, the reload's check waits.
        var checks = 0
        val checked = CompletableDeferred<MoonPayAccess>()
        val feature = open(
            moonPay = moonPay(
                check = {
                    if (checks++ < 1) {
                        MoonPayAccess.Connected
                    } else {
                        checked.await()
                    }
                },
            ),
        )
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertTrue(feature.quoting.value)
        assertFalse(feature.canContinue.value)
        coVerify(exactly = 1) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }

        checked.complete(MoonPayAccess.Connected)
        settle()
        coVerify(exactly = 2) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `another card on a recreated screen whose first load fails opens no picker after Retry`() {
        coEvery { source.findDefaultAsset(any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        val events = collectEvents(feature)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()
        assertTrue(feature.failed.value)
        coEvery { source.findDefaultAsset(any()) } returns Result.success(RampAsset(TestAssets.usdtTon, "TON", listOf("USD")))
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertFalse(feature.failed.value)
        assertTrue(events.none { it is BuyCryptoEvent.PickMethod })
    }

    @Test
    fun `another card on a recreated screen with nothing to pick opens no picker later`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(emptyList())
        val feature = open()
        val events = collectEvents(feature)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()
        coEvery { payments.loadMethods(any(), any(), any()) } answers { Result.success(listOf(method(ExchangePaymentMethodType.card))) }
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertTrue(events.none { it is BuyCryptoEvent.PickMethod })
    }

    @Test
    fun `nothing to pay with shows the placeholder`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(emptyList())

        val feature = open()

        assertNotNull(feature.nothingToBuy.value)
        assertFalse(feature.failed.value)
        assertNull(feature.payment.value)
        assertFalse(feature.canContinue.value)
        assertEquals(false, feature.amountField.value?.editable)
    }

    @Test
    fun `no asset to buy at all shows the placeholder in the preferred fiat`() {
        coEvery { source.findDefaultAsset(any()) } returns Result.success(null)

        val feature = open(preferredCurrency = "EUR")

        assertTrue(feature.nothingToBuy.value.orEmpty().contains("EUR"))
        assertEquals("EUR", feature.fiat.value)
        assertEquals(BuyCryptoReceive(amount = null, muted = false, asset = null, iconUrl = null, assetId = null), feature.receive.value)
        assertEquals(BuyCryptoSetup("EUR", assetId = null, method = null, canPickMethod = false), feature.currentSetup.value)
    }

    @Test
    fun `a failed quote shows in the rate row and cannot continue`() {
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        val events = collectEvents(feature)

        enter(feature, "50")
        settle()

        assertEquals(BuyCryptoRate.Failed, feature.rate.value)
        assertFalse(feature.canContinue.value)
        assertFalse(feature.quoting.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `the rate row reads the rate while it is quoted and turns around on a tap`() {
        val feature = open()
        assertNull(feature.rate.value)

        enter(feature, "50")
        assertEquals(BuyCryptoRate.Loading, feature.rate.value)
        settle()
        assertEquals(BuyCryptoRate.Quoted(from = "USD", to = "0.98 USDT (TON)", refreshing = false), feature.rate.value)

        feature.sendAction(BuyCryptoAction.ToggleRateDirection)
        scheduler.runCurrent()
        val inverted = feature.rate.value as BuyCryptoRate.Quoted
        assertEquals("USDT (TON)", inverted.from)
        assertEquals("1.02", inverted.to.filter { it.isDigit() || it == '.' })

        enter(feature, "")
        assertNull(feature.rate.value)
        assertFalse(feature.quoting.value)
    }

    @Test
    fun `MoonPay's quote on screen is broken down behind incl fees, a merchant's is not`() {
        val quote = moonPayQuote().copy(
            fees = mapOf(
                "moonpay" to MoonPayMoney(amount = "1.5", asset = MoonPayAsset(code = "usd")),
                "ecosystem" to MoonPayMoney(amount = "0.5", asset = MoonPayAsset(code = "usd")),
            ),
        )
        coEvery { moonPayPayments.requestOffer(any()) } answers {
            Result.success((available(firstArg()) as RampOffer.Available).copy(ticket = RampTicket.MoonPay(quote)))
        }
        val feature = open(moonPay = moonPay())
        assertNull(feature.priceDetails.value)

        enter(feature, "50")
        assertNull(feature.priceDetails.value)
        settle()

        val details = requireNotNull(feature.priceDetails.value)
        assertEquals(feature.rate.value, details.rate)
        assertEquals(listOf(MoonPayPaymentFeeKind.MoonPay, MoonPayPaymentFeeKind.Ecosystem), details.fees.map { it.kind })
        assertTrue(details.youPay.contains("50"))
        assertTrue(details.youGet.endsWith("USDT (TON)"))

        enter(feature, "")
        assertNull(feature.priceDetails.value)
    }

    @Test
    fun `price details keep their link but don't open while Continue runs, and open again after it`() {
        coEvery { moonPayPayments.requestOffer(any()) } answers {
            Result.success((available(firstArg()) as RampOffer.Available).copy(ticket = RampTicket.MoonPay(moonPayQuote())))
        }
        val checkout = CompletableDeferred<Result<RampCheckout>>()
        coEvery { moonPayPayments.checkout(any(), any()) } coAnswers { checkout.await() }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()
        assertTrue(feature.priceDetailsOpenable.value)

        feature.sendAction(BuyCryptoAction.Continue)
        scheduler.runCurrent()
        assertTrue(feature.continuing.value)
        assertNotNull(feature.priceDetails.value)
        assertFalse(feature.priceDetailsOpenable.value)

        checkout.complete(Result.success(RampCheckout.Cancelled))
        settle()
        assertNotNull(feature.priceDetails.value)
        assertTrue(feature.priceDetailsOpenable.value)
    }

    @Test
    fun `a merchant's offer has no price details`() {
        val feature = open()
        enter(feature, "50")
        settle()

        assertTrue(feature.rate.value is BuyCryptoRate.Quoted)
        assertNull(feature.priceDetails.value)
    }

    @Test
    fun `the rate is refreshed when its timer runs out, keeping the offer on screen`() {
        val feature = open()
        enter(feature, "50")
        settle()
        val pending = CompletableDeferred<Result<RampOffer>>()
        coEvery { payments.requestOffer(any()) } coAnswers { pending.await() }

        pass(RATE_LIFETIME - 1.seconds)
        coVerify(exactly = 1) { payments.requestOffer(any()) }
        pass(1.seconds)

        coVerify(exactly = 2) { payments.requestOffer(any()) }
        assertFalse(feature.quoting.value)
        assertEquals("49", feature.receive.value?.amount)
        assertEquals(BuyCryptoRate.Quoted(from = "USD", to = "0.98 USDT (TON)", refreshing = true), feature.rate.value)
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `offline the last rate stays with the spinner and the amount at it, greyed, without Continue`() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))

        pass(RATE_LIFETIME)

        assertEquals(BuyCryptoRate.Quoted(from = "USD", to = "0.98 USDT (TON)", refreshing = true), feature.rate.value)
        assertEquals("49", feature.receive.value?.amount)
        assertTrue(feature.receive.value?.muted == true)
        assertFalse(feature.canContinue.value)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Offline), events)
    }

    @Test
    fun `offline a refresh tells once in 30 s, every input of the user tells`() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))

        pass(RATE_LIFETIME)
        pass(RATE_LIFETIME)
        assertEquals(1, events.count { it is BuyCryptoEvent.Offline })

        enter(feature, "100")
        settle()
        assertEquals(2, events.count { it is BuyCryptoEvent.Offline })
        assertEquals("98", feature.receive.value?.amount)
    }

    @Test
    fun `back online the rate comes back`() {
        val feature = open()
        enter(feature, "50")
        settle()
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        pass(RATE_LIFETIME)

        coEvery { payments.requestOffer(any()) } answers { Result.success(available(firstArg())) }
        online.value = true
        settle()

        assertTrue(feature.canContinue.value)
        assertTrue(feature.receive.value?.muted == false)
    }

    @Test
    fun `offline with no rate yet it is still getting the rate`() {
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        val feature = open()

        enter(feature, "50")
        settle()

        assertEquals(BuyCryptoRate.Loading, feature.rate.value)
        assertFalse(feature.canContinue.value)
    }

    @Test
    fun `back online a quote that failed for the network asks again at once, with no rate yet too`() {
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        enter(feature, "50")
        settle()

        coEvery { payments.requestOffer(any()) } answers { Result.success(available(firstArg())) }
        online.value = true
        settle()

        assertTrue(feature.canContinue.value)
        assertEquals("49", feature.receive.value?.amount)
    }

    @Test
    fun `offline with no rate yet the timer keeps asking`() {
        online.value = false
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))
        val feature = open()
        enter(feature, "50")
        settle()

        // The network works before the monitor says so: only the timer asks again.
        coEvery { payments.requestOffer(any()) } answers { Result.success(available(firstArg())) }
        pass(RATE_LIFETIME)

        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `an unreachable network is offline before the monitor knows`() {
        val feature = open()
        val events = collectEvents(feature)
        coEvery { payments.requestOffer(any()) } returns Result.failure(java.net.ConnectException("connect failed: ENETUNREACH (Network is unreachable)"))

        enter(feature, "50")
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Offline), events)
    }

    @Test
    fun `a refused connection on a working network is a failed rate, not offline`() {
        val feature = open()
        val events = collectEvents(feature)
        coEvery { payments.requestOffer(any()) } returns Result.failure(java.net.ConnectException("refused"))

        enter(feature, "50")
        settle()

        assertEquals(BuyCryptoRate.Failed, feature.rate.value)
        assertTrue(events.none { it is BuyCryptoEvent.Offline })
    }

    @Test
    fun `a request that could not reach the host is offline before the monitor knows`() {
        val feature = open()
        val events = collectEvents(feature)
        coEvery { payments.requestOffer(any()) } returns Result.failure(java.net.UnknownHostException("swap"))

        enter(feature, "50")
        settle()

        assertEquals(BuyCryptoRate.Loading, feature.rate.value)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Offline), events)
    }

    @Test
    fun `a failure offline says there is no internet, not an unknown error`() {
        val feature = open()
        val events = collectEvents(feature)
        online.value = false
        coEvery { source.loadAssetDetail(any()) } returns Result.failure(IOException("offline"))

        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Offline), events)
    }

    @Test
    fun `a failed refresh drops the offer until a retry gets a new one`() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))

        pass(RATE_LIFETIME)
        pass(RATE_LIFETIME)

        coVerify(exactly = 2) { payments.requestOffer(any()) }
        assertEquals(BuyCryptoRate.Failed, feature.rate.value)
        assertNull(feature.receive.value?.amount)
        assertFalse(feature.canContinue.value)
        assertTrue(events.isEmpty())

        coEvery { payments.requestOffer(any()) } answers { Result.success(available(firstArg())) }
        feature.sendAction(BuyCryptoAction.Requote)
        scheduler.runCurrent()

        assertEquals(BuyCryptoRate.Quoted(from = "USD", to = "0.98 USDT (TON)", refreshing = false), feature.rate.value)
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `in the background the rate waits and is refreshed on return`() {
        val feature = open()
        enter(feature, "50")
        settle()

        foreground.value = false
        pass(RATE_LIFETIME * 2)
        coVerify(exactly = 1) { payments.requestOffer(any()) }

        foreground.value = true
        scheduler.runCurrent()

        coVerify(exactly = 2) { payments.requestOffer(any()) }
        assertEquals(BuyCryptoRate.Quoted(from = "USD", to = "0.98 USDT (TON)", refreshing = false), feature.rate.value)
    }

    @Test
    fun `an amount out of limits shows the limit in full instead of the rate`() {
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("1500.50"))),
        )
        val feature = open()

        enter(feature, "10")
        settle()
        pass(RATE_LIFETIME)

        val limit = feature.limit.value as BuyCryptoLimit.BelowMin
        assertEquals("1500.5", limit.amount.filter { it.isDigit() || it == '.' })
        assertNull(feature.rateTimer.value)
        coVerify(exactly = 1) { payments.requestOffer(any()) }
    }

    @Test
    fun `the limit stays while another amount of the same purchase is quoted, and goes with an amount within it`() {
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("20"))),
        )
        val feature = open()
        enter(feature, "10")
        settle()
        val pending = CompletableDeferred<Unit>()
        coEvery { payments.requestOffer(any()) } coAnswers {
            pending.await()
            Result.success(available(firstArg()))
        }

        enter(feature, "25")
        settle()

        assertTrue(feature.limit.value is BuyCryptoLimit.BelowMin)
        assertTrue(feature.amountField.value!!.outOfLimits)
        assertFalse(feature.canContinue.value)

        pending.complete(Unit)
        settle()

        assertNull(feature.limit.value)
        assertFalse(feature.amountField.value!!.outOfLimits)
    }

    @Test
    fun `a pick of another payment method drops the limit while the new one is quoted`() {
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("20"))),
        )
        val feature = open()
        enter(feature, "10")
        settle()
        assertTrue(feature.limit.value is BuyCryptoLimit.BelowMin)
        coEvery { payments.requestOffer(any()) } coAnswers { CompletableDeferred<Result<RampOffer>>().await() }

        feature.sendAction(BuyCryptoAction.SelectMethod("paypal", "USD", "PayPal", "Instant", null, null, false))
        settle()

        assertNull(feature.limit.value)
        assertFalse(feature.amountField.value?.outOfLimits ?: false)
    }

    @Test
    fun `out of limits the last rate of the same purchase stays`() {
        val feature = open()
        enter(feature, "50")
        settle()
        val quoted = feature.rate.value
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("20"))),
        )

        enter(feature, "10")
        settle()

        assertTrue(quoted is BuyCryptoRate.Quoted)
        assertEquals(quoted, feature.rate.value)
        assertTrue(feature.limit.value is BuyCryptoLimit.BelowMin)

        enter(feature, "")
        enter(feature, "1")
        settle()

        assertEquals(quoted, feature.rate.value)
    }

    @Test
    fun `an offer without a rate is not refreshed`() {
        coEvery { payments.requestOffer(any()) } answers {
            Result.success(RampOffer.Available(firstArg(), receive = BigDecimal("49"), rate = null, lifetime = null, badgeUrl = badge, ticket = ticket))
        }
        val feature = open()
        enter(feature, "50")
        settle()

        pass(RATE_LIFETIME * 2)

        assertNull(feature.rate.value)
        assertTrue(feature.canContinue.value)
        coVerify(exactly = 1) { payments.requestOffer(any()) }
    }

    @Test
    fun `a limit shows at the fiat's precision the way it is checked`() {
        coEvery { payments.requestOffer(any()) } returns Result.success(
            RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("16.6833"))),
        )
        val feature = open()

        enter(feature, "10")
        settle()

        val limit = feature.limit.value as BuyCryptoLimit.BelowMin
        assertEquals("16.68", limit.amount.filter { it.isDigit() || it == '.' })
    }

    @Test
    fun `P2P has no rate row`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(listOf(p2p))
        val feature = open()

        enter(feature, "50")
        assertNull(feature.rate.value)
        settle()

        assertNull(feature.rate.value)
        assertFalse(feature.quoting.value)
    }

    @Test
    fun `a refresh that lands on a new amount never shows the old offer for it`() {
        val feature = open()
        enter(feature, "50")
        settle()
        coEvery { payments.requestOffer(any()) } returns Result.failure(IOException("offline"))

        enter(feature, "60")
        feature.sendAction(BuyCryptoAction.Requote)
        scheduler.runCurrent()
        assertNull(feature.receive.value?.amount)
        assertFalse(feature.canContinue.value)
        settle()

        assertNull(feature.receive.value?.amount)
        assertEquals(BuyCryptoRate.Failed, feature.rate.value)
        assertFalse(feature.canContinue.value)
    }

    @Test
    fun `an empty amount takes the merchant's minimum and the quick amounts follow it`() {
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25.00"), BigDecimal("29000")))

        val feature = open()

        assertEquals("25", feature.amount.value.text)
        val quickAmounts = feature.quickAmounts.value!!
        assertEquals(listOf("50", "100", "500"), quickAmounts.items.map { it.amount })
        assertEquals("29000", quickAmounts.max)
        assertFalse(quickAmounts.loading)
        coVerify { payments.requestOffer(match { it.amount.compareTo(BigDecimal("25")) == 0 }) }
    }

    @Test
    fun `a field the user emptied stays empty after a recreation`() {
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25"), null))
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        assertEquals("25", feature.amount.value.text)

        enter(feature, "")
        val recreated = open(handle = handle)

        assertEquals("", recreated.amount.value.text)
    }

    @Test
    fun `a rejected input is still answered, so the field rewinds to the kept amount`() {
        val feature = open()
        enter(feature, "12")
        val before = feature.amount.value

        enter(feature, "12345678901234567")

        assertEquals("12", feature.amount.value.text)
        assertTrue(feature.amount.value.revision > before.revision)
    }

    @Test
    fun `a field typed into before the minimum arrives keeps what the user left`() {
        val range = MutableStateFlow(RampAmountRange(null, null))
        coEvery { payments.loadAmountRange(any(), any()) } answers { Result.success(range.value) }
        val feature = open()

        enter(feature, "5")
        enter(feature, "")
        range.value = RampAmountRange(BigDecimal("25"), null)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals("", feature.amount.value.text)
    }

    @Test
    fun `the minimum typed in by the user is the user's`() {
        val range = MutableStateFlow(RampAmountRange(BigDecimal("25"), null))
        coEvery { payments.loadAmountRange(any(), any()) } answers { Result.success(range.value) }
        val feature = open()

        enter(feature, "30")
        enter(feature, "25")
        range.value = RampAmountRange(BigDecimal("30"), null)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals("25", feature.amount.value.text)
    }

    @Test
    fun `quick amounts above the merchants' maximum are left to MAX`() {
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25"), BigDecimal("300")))

        val feature = open()

        assertEquals(listOf("50", "100"), feature.quickAmounts.value!!.items.map { it.amount })
        assertEquals("300", feature.quickAmounts.value!!.max)
    }

    @Test
    fun `P2P has no quick amounts`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(listOf(p2p))

        val feature = open()

        assertNull(feature.quickAmounts.value)
    }

    @Test
    fun `a quick amount and MAX are typed into the field`() {
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25"), BigDecimal("29000")))
        val feature = open()

        feature.sendAction(BuyCryptoAction.SetAmount(feature.quickAmounts.value!!.items[1].amount))
        scheduler.runCurrent()
        assertEquals("100", feature.amount.value.text)

        feature.sendAction(BuyCryptoAction.SetAmount(feature.quickAmounts.value!!.max!!))
        scheduler.runCurrent()
        assertEquals("29000", feature.amount.value.text)
    }

    @Test
    fun `a typed or restored amount is never replaced by the minimum`() {
        val range = MutableStateFlow(RampAmountRange(BigDecimal("25"), null))
        coEvery { payments.loadAmountRange(any(), any()) } answers { Result.success(range.value) }
        val handle = SavedStateHandle(mapOf("buy_crypto_amount" to "70"))

        val restored = open(handle = handle)
        assertEquals("70", restored.amount.value.text)

        val feature = open()
        enter(feature, "40")
        range.value = RampAmountRange(BigDecimal("30"), null)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals("40", feature.amount.value.text)
    }

    @Test
    fun `a new minimum replaces the one put in before`() {
        val range = MutableStateFlow(RampAmountRange(BigDecimal("25"), null))
        coEvery { payments.loadAmountRange(any(), any()) } answers { Result.success(range.value) }
        val feature = open()
        assertEquals("25", feature.amount.value.text)

        range.value = RampAmountRange(BigDecimal("30"), null)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals("30", feature.amount.value.text)
    }

    @Test
    fun `without limits the quick amounts are the defaults and there is no MAX`() {
        coEvery { payments.loadAmountRange(any(), any()) } returns Result.failure(IOException("offline"))

        val feature = open()

        assertEquals("", feature.amount.value.text)
        assertEquals(listOf("50", "100", "500"), feature.quickAmounts.value!!.items.map { it.amount })
        assertNull(feature.quickAmounts.value!!.max)
    }

    @Test
    fun `nothing to buy has no quick amounts`() {
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(emptyList())

        val feature = open()

        assertNull(feature.quickAmounts.value)
    }

    @Test
    fun `a chosen currency is kept for the asset and the amount takes its minimum`() {
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25"), null))
        coEvery { payments.loadAmountRange(card, "EUR") } returns Result.success(RampAmountRange(BigDecimal("20"), null))
        val feature = open()
        enter(feature, "70")

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals("EUR", feature.fiat.value)
        assertEquals("20", feature.amount.value.text)
        coVerify { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") }
        coVerify(exactly = 0) { source.resolveFiat(asset, "EUR") }
    }

    @Test
    fun `a chosen currency the asset is not sold for shows the asset's stub in it`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } returns Result.success(emptyList())
        val feature = open(assetId = TestAssets.usdtTon.id)

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals("EUR", feature.fiat.value)
        assertEquals(TestAssets.usdtTon.id, feature.currentSetup.value?.assetId)
        assertTrue(feature.nothingToBuy.value.orEmpty().contains("EUR"))
        coVerify(exactly = 0) { source.findDefaultAsset(any()) }
    }

    @Test
    fun `a DeFi token MoonPay refuses to quote goes to the merchants`() {
        val defi = moonPayMethod("card").copy(details = RampPaymentMethod.Details.MoonPay("card", TestAssets.usdtTon.id, MoonPayAsset(caip19 = "eip155:1/erc20:0xaave")))
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(defi))
        coEvery { moonPayPayments.loadAmountRange(any(), any()) } returns Result.failure(MoonPayApiException.Http(503, null))

        val feature = open(assetId = TestAssets.usdtTon.id, moonPay = moonPay())
        settle()

        assertNull(feature.nothingToBuy.value)
        coVerify(exactly = 1) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        coVerify(atLeast = 1) { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        assertEquals(BuyCryptoPaymentRow.Method(BuyCryptoPayment("card", null, null, null, card = null), loading = false), feature.payment.value)
    }

    @Test
    fun `MoonPay failing for a token the merchants do not sell is a failed load, not nothing to buy`() {
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.failure(IOException("offline"))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)

        val feature = open(assetId = TestAssets.usdtTon.id, moonPay = moonPay())

        assertTrue(feature.failed.value)
        assertNull(feature.nothingToBuy.value)
    }

    @Test
    fun `MoonPay refusing a pair the merchants do not sell either shows the stub, not a retry`() {
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.failure(MoonPayApiException.Http(400, null))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)

        val feature = open(assetId = TestAssets.usdtTon.id, moonPay = moonPay())

        assertFalse(feature.failed.value)
        assertTrue(feature.nothingToBuy.value != null)
    }

    @Test
    fun `an expired MoonPay session for a pair the merchants do not sell is a failed load`() {
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.failure(MoonPayApiException.Http(401, null))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)

        val feature = open(assetId = TestAssets.usdtTon.id, moonPay = moonPay())

        assertTrue(feature.failed.value)
    }

    @Test
    fun `a 503 for a token MoonPay quotes by its code is an outage, not a hand-over`() {
        coEvery { moonPayPayments.loadAmountRange(any(), any()) } returns Result.failure(MoonPayApiException.Http(503, null))

        val feature = open(assetId = TestAssets.usdtTon.id, moonPay = moonPay())
        settle()

        coVerify(exactly = 1) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        coVerify(exactly = 0) { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
    }

    @Test
    fun `a chosen currency nothing is sold for shows the placeholder in it`() {
        coEvery { payments.loadMethods(any(), any(), "KGS") } returns Result.success(emptyList())
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectFiat("KGS"))
        settle()

        assertEquals("KGS", feature.fiat.value)
        assertTrue(feature.nothingToBuy.value.orEmpty().contains("KGS"))
    }

    @Test
    fun `a currency picked from the placeholder keeps the field shut until it loads`() {
        coEvery { payments.loadMethods(any(), any(), "KGS") } returns Result.success(emptyList())
        val feature = open()
        feature.sendAction(BuyCryptoAction.SelectFiat("KGS"))
        settle()
        val pending = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { payments.loadMethods(any(), any(), "EUR") } coAnswers { pending.await() }

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals(false, feature.amountField.value?.editable)
        pending.complete(Result.success(listOf(card)))
        settle()
        assertEquals(true, feature.amountField.value?.editable)
    }

    @Test
    fun `a failed pick from the placeholder keeps the field shut`() {
        coEvery { payments.loadMethods(any(), any(), "KGS") } returns Result.success(emptyList())
        val feature = open()
        feature.sendAction(BuyCryptoAction.SelectFiat("KGS"))
        settle()
        coEvery { payments.loadMethods(any(), any(), "EUR") } returns Result.failure(IOException("offline"))

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertNull(feature.nothingToBuy.value)
        assertEquals(false, feature.amountField.value?.editable)
    }

    @Test
    fun `a chosen asset keeps the typed amount`() {
        val ton = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(ton)
        coEvery { source.resolveFiat(ton, any()) } answers { secondArg() }
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(card))
        val feature = open()
        enter(feature, "70")

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", null))
        settle()

        assertEquals("70", feature.amount.value.text)
        assertEquals("TON", feature.receive.value?.asset)
        coVerify { payments.requestOffer(match { it.assetId == TestAssets.ton.id && it.amount.compareTo(BigDecimal("70")) == 0 }) }
    }

    @Test
    fun `a chosen method is quoted, and one the asset lacks gives way to the first`() {
        val sepa = method(ExchangePaymentMethodType.sepa)
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(card, sepa, p2p))
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectMethod("sepa", "USD", "method", null, null, null, false))
        settle()
        enter(feature, "50")
        settle()
        assertEquals("sepa", feature.currentSetup.value?.method)
        coVerify { payments.requestOffer(match { it.method == sepa }) }

        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } returns Result.success(listOf(card))
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()
        assertEquals("card", feature.currentSetup.value?.method)
    }

    @Test
    fun `a pick shows at once and nothing can be ordered until it loads`() {
        val methods = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        val feature = open()
        enter(feature, "50")
        settle()
        assertTrue(feature.canContinue.value)
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } coAnswers { methods.await() }

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertFalse(feature.loading.value)
        assertEquals("EUR", feature.fiat.value)
        assertTrue(feature.quoting.value)
        assertEquals(BuyCryptoRate.Loading, feature.rate.value)
        assertNull(feature.receive.value?.amount)
        assertTrue((feature.payment.value as BuyCryptoPaymentRow.Method).loading)
        assertEquals(false, feature.currentSetup.value?.canPickMethod)
        assertFalse(feature.canContinue.value)
        feature.sendAction(BuyCryptoAction.Continue)
        scheduler.runCurrent()
        coVerify(exactly = 0) { payments.checkout(any(), any()) }

        methods.complete(Result.success(listOf(card)))
        settle()
        assertEquals("EUR", feature.currentSetup.value?.fiat)
    }

    @Test
    fun `a picked asset and method show at once`() {
        val ton = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } coAnswers { loaded.await() }
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "BTC", "Bitcoin", "https://img/btc"))
        settle()
        assertEquals(BuyCryptoReceive(amount = null, muted = false, asset = "BTC (Bitcoin)", iconUrl = "https://img/btc", assetId = TestAssets.ton.id), feature.receive.value)

        feature.sendAction(BuyCryptoAction.SelectMethod("paypal", "USD", "PayPal", "Instant", null, null, false))
        settle()
        assertEquals(
            BuyCryptoPaymentRow.Method(BuyCryptoPayment("PayPal", "Instant", null, null, card = null), loading = true),
            feature.payment.value,
        )
        loaded.complete(Result.success(ton))
    }

    @Test
    fun `a pick that fails to load keeps the screen and can be retried`() {
        val feature = open()
        val events = collectEvents(feature)
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } returns Result.failure(IOException("offline"))

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertFalse(feature.failed.value)
        assertEquals("EUR", feature.fiat.value)
        assertEquals(BuyCryptoPaymentRow.Failed, feature.payment.value)
        assertNull(feature.rate.value)
        assertFalse(feature.canContinue.value)
        assertEquals("EUR", feature.currentSetup.value?.fiat)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), events)

        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } returns Result.success(listOf(card))
        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        assertEquals(BuyCryptoPaymentRow.Method(BuyCryptoPayment("card", null, null, null, card = null), loading = false), feature.payment.value)
    }

    @Test
    fun `a chosen asset keeps the currency on screen`() {
        val ton = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(ton)
        coEvery { source.resolveFiat(ton, any()) } returns "EUR"
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(card))
        val feature = open()
        enter(feature, "70")

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", null))
        settle()

        assertEquals("USD", feature.fiat.value)
        assertEquals("70", feature.amount.value.text)
        coVerify(exactly = 0) { source.resolveFiat(ton, any()) }
    }

    @Test
    fun `nothing can be picked while an order is placed or opened`() {
        val order = CompletableDeferred<Result<RampCheckout>>()
        coEvery { payments.checkout(any(), any()) } coAnswers { order.await() }
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertNull(feature.currentSetup.value)

        order.complete(Result.success(RampCheckout.OpenUrl("https://widget")))
        settle()
        assertNull(feature.currentSetup.value)
    }

    @Test
    fun `picking what is on screen changes nothing`() {
        val feature = open()
        enter(feature, "70")
        settle()

        feature.sendAction(BuyCryptoAction.SelectFiat("USD"))
        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.usdtTon.id, "USD", "TON", "TON", null))
        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "method", null, null, null, false))
        scheduler.runCurrent()

        assertEquals("70", feature.amount.value.text)
        assertTrue(feature.canContinue.value)
        coVerify(exactly = 1) { payments.loadMethods(any(), any(), any()) }
    }

    @Test
    fun `a new currency keeps the asset on screen when it is sold there`() {
        val ton = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(ton)
        coEvery { source.resolveFiat(ton, any()) } answers { secondArg() }
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(card))
        coEvery { source.findDefaultAsset("USD") } returns Result.success(RampAsset(TestAssets.ton, "TON", listOf("USD")))
        val feature = open()
        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)
        coVerify(exactly = 0) { source.findDefaultAsset("EUR") }
    }

    @Test
    fun `an asset picked before the recreated screen loads waits for it and keeps its currency`() {
        val ton = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } coAnswers { loaded.await() }
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(ton)
        coEvery { source.resolveFiat(ton, any()) } returns "USD"
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(card))
        val feature = open(assetId = TestAssets.usdtTon.id, preferredCurrency = "EUR")
        assertNull(feature.currentSetup.value)

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "EUR", "TON", "TON", null))
        settle()
        coVerify(exactly = 0) { source.loadAssetDetail(TestAssets.ton.id) }
        loaded.complete(Result.success(asset))
        settle()

        assertEquals("EUR", feature.fiat.value)
        coVerify { payments.loadMethods(TestAssets.ton.id, any(), "EUR") }
    }

    @Test
    fun `a method picked over a fallback stays picked when the currency comes back`() {
        val sepa = method(ExchangePaymentMethodType.sepa)
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } returns Result.success(listOf(card, sepa))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "USD") } returns Result.success(listOf(card))
        val feature = open(preferredCurrency = "EUR")
        feature.sendAction(BuyCryptoAction.SelectMethod("sepa", "EUR", "SEPA", null, null, null, false))
        settle()
        feature.sendAction(BuyCryptoAction.SelectFiat("USD"))
        settle()
        assertEquals("card", feature.currentSetup.value?.method)

        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "card", null, null, null, false))
        settle()
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals("card", feature.currentSetup.value?.method)
    }

    @Test
    fun `the amount is typed in the picked currency while it loads, and not where nothing is sold`() {
        val methods = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        val feature = open()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "JPY") } coAnswers { methods.await() }

        feature.sendAction(BuyCryptoAction.SelectFiat("JPY"))
        settle()
        assertEquals(BuyCryptoAmountField(fiat = "JPY", editable = true, outOfLimits = false, grouping = GROUPING), feature.amountField.value)
        enter(feature, "12.5")
        assertFalse(feature.amount.value.text, feature.amount.value.text.contains('5'))

        coEvery { source.findDefaultAsset("JPY") } returns Result.success(null)
        methods.complete(Result.success(emptyList()))
        settle()
        assertEquals(BuyCryptoAmountField(fiat = "JPY", editable = false, outOfLimits = false, grouping = GROUPING), feature.amountField.value)
    }

    @Test
    fun `no placeholder chips while a pick loads after P2P`() {
        val methods = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { payments.loadMethods(any(), any(), any()) } returns Result.success(listOf(p2p))
        val feature = open()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), "EUR") } coAnswers { methods.await() }

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertNull(feature.quickAmounts.value)
        methods.complete(Result.success(listOf(p2p)))
    }

    @Test
    fun `a picked asset without methods keeps its stub and is checked again in the next currency`() {
        val btc = asset.copy(assetId = TestAssets.ton.id, symbol = "BTC")
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(btc)
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(emptyList())
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "BTC", null, null))
        settle()
        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)
        assertTrue(feature.nothingToBuy.value != null)

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()
        assertEquals(TestAssets.ton.id, feature.currentSetup.value?.assetId)
        coVerify(exactly = 1) { payments.loadMethods(TestAssets.ton.id, any(), "EUR") }
    }

    @Test
    fun `a pick made before the first load is applied against what loads`() {
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } coAnswers { loaded.await() }
        coEvery { payments.loadAmountRange(card, "USD") } returns Result.success(RampAmountRange(BigDecimal("25"), null))
        val feature = open(handle = SavedStateHandle(mapOf("buy_crypto_amount" to "70")))

        feature.sendAction(BuyCryptoAction.SelectFiat("USD"))
        settle()
        loaded.complete(Result.success(asset))
        settle()

        assertEquals("70", feature.amount.value.text)
        coVerify(exactly = 1) { payments.loadMethods(any(), any(), any()) }
    }

    @Test
    fun `MoonPay's amount screen picked before the first load is reported for the pick, not for what it replaced`() {
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        val other = asset.copy(assetId = TestAssets.ton.id, symbol = "TON")
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } coAnswers { loaded.await() }
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(other)
        coEvery { source.resolveFiat(other, any()) } answers { secondArg() }
        coEvery { moonPayPayments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(moonPayMethod("card")))
        val feature = open(moonPay = moonPay())

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", iconUrl = null))
        settle()
        loaded.complete(Result.success(asset))
        settle()

        assertEquals(listOf(TestAssets.ton.id), sent("deposit_view_ramp_insert_amount").map { it["buy_asset"] })
    }

    @Test
    fun `the rate is not refreshed under a picker and is on return`() {
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.SetVisible(false))
        pass(RATE_LIFETIME * 2)
        coVerify(exactly = 1) { payments.requestOffer(any()) }

        feature.sendAction(BuyCryptoAction.SetVisible(true))
        settle()
        coVerify(exactly = 2) { payments.requestOffer(any()) }
    }

    @Test
    fun `a pick that waited for a first load that failed is what Reload loads`() {
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } coAnswers { loaded.await() }
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()
        loaded.complete(Result.failure(IOException("offline")))
        settle()
        assertTrue(feature.failed.value)

        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } returns Result.success(asset)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals("EUR", feature.fiat.value)
        coVerify(exactly = 0) { payments.loadMethods(TestAssets.usdtTon.id, any(), "USD") }
    }

    @Test
    fun `of picks made while the first load runs the last one counts`() {
        val loaded = CompletableDeferred<Result<RampAssetDetail?>>()
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } coAnswers { loaded.await() }
        val sepa = method(ExchangePaymentMethodType.sepa)
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(card, sepa))
        val feature = open()

        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "card", null, null, null, false))
        feature.sendAction(BuyCryptoAction.SelectMethod("sepa", "USD", "SEPA", null, null, null, false))
        settle()
        loaded.complete(Result.success(asset))
        settle()

        assertEquals("sepa", feature.currentSetup.value?.method)
    }

    @Test
    fun `picks survive a recreation`() {
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        val recreated = open(handle = handle)

        assertEquals("EUR", recreated.fiat.value)
    }

    @Test
    fun `the catalog loads during MoonPay's check, the methods wait for it, then MoonPay sells`() {
        val connected = CompletableDeferred<MoonPayAccess>()
        val feature = open(moonPay = moonPay(check = { connected.await() }))

        assertTrue(feature.loading.value)
        coVerify { source.loadAssetDetail(TestAssets.usdtTon.id) }
        coVerify(exactly = 0) { payments.loadMethods(any(), any(), any()) }
        coVerify(exactly = 0) { moonPayPayments.loadMethods(any(), any(), any()) }
        connected.complete(MoonPayAccess.Connected)
        settle()
        // MoonPay's badge is on its method before any quote, as on iOS.
        assertEquals(BuyCryptoBadge.MoonPay, (feature.payment.value as? BuyCryptoPaymentRow.Method)?.payment?.badge)
        enter(feature, "50")
        settle()

        assertFalse(feature.loading.value)
        assertEquals(true, feature.onMoonPay)
        assertEquals(BuyCryptoBadge.MoonPay, (feature.payment.value as? BuyCryptoPaymentRow.Method)?.payment?.badge)
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        coVerify(exactly = 0) { payments.requestOffer(any()) }
    }

    @Test
    fun `without a MoonPay connection the merchants sell`() {
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }))

        assertEquals(false, feature.onMoonPay)
        coVerify(exactly = 0) { moonPayPayments.loadMethods(any(), any(), any()) }
    }

    @Test
    fun `an asset MoonPay does not sell goes to the merchants, the next one back to MoonPay with the method kept`() {
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(asset.copy(assetId = TestAssets.ton.id))
        coEvery { moonPayPayments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(moonPayMethod("revolut_pay"), moonPayMethod("card")))
        val feature = open(moonPay = moonPay())
        assertEquals(false, feature.onMoonPay)
        assertEquals("card", feature.currentSetup.value?.method)

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", null))
        settle()

        assertEquals(true, feature.onMoonPay)
        assertEquals("card", feature.currentSetup.value?.method)
    }

    @Test
    fun `the rate lasts as long as MoonPay holds the quote, less a second`() {
        coEvery { moonPayPayments.requestOffer(any()) } answers { Result.success(available(firstArg(), lifetime = 20.seconds)) }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        pass(RATE_LIFETIME)
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        pass(19.seconds - RATE_LIFETIME)

        coVerify(exactly = 2) { moonPayPayments.requestOffer(any()) }
    }

    @Test
    fun `a MoonPay quote without a rate still runs out`() {
        coEvery { moonPayPayments.requestOffer(any()) } answers {
            Result.success((available(firstArg(), lifetime = 20.seconds) as RampOffer.Available).copy(rate = null))
        }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        pass(20.seconds)

        coVerify(exactly = 2) { moonPayPayments.requestOffer(any()) }
    }

    @Test
    fun `retrying the rate on MoonPay checks the connection again, its timer never does`() {
        var connects = 0
        val feature = open(moonPay = moonPay(check = { connects++; MoonPayAccess.Connected }))
        enter(feature, "50")
        settle()

        pass(RATE_LIFETIME)
        assertEquals(1, connects)
        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()

        assertEquals(2, connects)
        coVerify(exactly = 3) { moonPayPayments.requestOffer(any()) }
    }

    @Test
    fun `retrying on the merchants never checks MoonPay again`() {
        var connects = 0
        val feature = open(moonPay = moonPay(check = { connects++; MoonPayAccess.SignInRequired }))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.RetryRate)
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(1, connects)
    }

    @Test
    fun `a quote about to run out still gets three seconds of rate`() {
        coEvery { moonPayPayments.requestOffer(any()) } answers { Result.success(available(firstArg(), lifetime = 1.seconds)) }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        pass(2.seconds)
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        pass(1.seconds)

        coVerify(exactly = 2) { moonPayPayments.requestOffer(any()) }
    }

    @Test
    fun `back from the status sheet the screen is as it was and Continue pays again`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Pay(data))
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        feature.sendAction(BuyCryptoAction.SetVisible(false))
        feature.sendAction(BuyCryptoAction.SetVisible(true))
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenMoonPay(data.copy(assetLabel = "USDT (TON)", report = report("card", "Mercuryo")))), events)
        assertEquals(false, feature.amountField.value?.editable)
        assertFalse(feature.canContinue.value)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.Again))
        settle()
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        assertEquals(true, feature.amountField.value?.editable)
        assertTrue(feature.canContinue.value)

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        coVerify(exactly = 2) { moonPayPayments.checkout(any(), any()) }
    }

    @Test
    fun `a failed challenge is shown and loads the methods again`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.ReloadAfterFailure)
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        val reloaded = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { reloaded.await() }

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), events)
        assertFalse(feature.canContinue.value)

        reloaded.complete(Result.success(listOf(moonPayMethod("card"))))
        settle()
        coVerify(exactly = 2) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        coVerify(exactly = 2) { moonPayPayments.requestOffer(any()) }
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `Retry after a failed first load checks MoonPay that connected on open`() {
        coEvery { source.findDefaultAsset(any()) } returns Result.failure(IOException("offline"))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { signIns++; MoonPayAccess.Connected }))
        settle()
        assertTrue(feature.failed.value)
        assertEquals(1, signIns)

        coEvery { source.findDefaultAsset(any()) } returns Result.success(RampAsset(TestAssets.usdtTon, "TON", listOf("USD")))
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(2, signIns)
        assertFalse(feature.failed.value)
    }

    @Test
    fun `a challenge passed loads the methods again`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Reload)
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        coVerify(exactly = 2) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `continue on MoonPay without signing in stays quiet and can be tried again`() {
        val signIns = ArrayDeque(listOf(MoonPaySignIn.Closed, MoonPaySignIn.SignedIn))
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        val feature = open(moonPay = moonPay(connect = { signIns.removeFirst() }))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        coVerify(exactly = 0) { moonPayPayments.checkout(any(), any()) }
        assertTrue(events.isEmpty())
        assertTrue(feature.canContinue.value)

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        coVerify(exactly = 1) { moonPayPayments.checkout(any(), any()) }
    }

    @Test
    fun `another card before the screen has loaded opens no picker`() {
        val methods = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { methods.await() }
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()
        methods.complete(Result.success(listOf(moonPayMethod("card"))))
        settle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a rate that runs out during a checkout ending in Pay is not requoted while the frame opens`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } coAnswers {
            delay(RATE_LIFETIME * 2)
            Result.success(RampCheckout.Pay(data))
        }
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        pass(RATE_LIFETIME * 2)
        pass(RATE_LIFETIME)

        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        assertFalse(feature.canContinue.value)

        feature.sendAction(BuyCryptoAction.PaymentEnded(retry = null))
        settle()
        coVerify(exactly = 2) { moonPayPayments.requestOffer(any()) }
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `nothing is picked or quoted while MoonPay pays, and another card opens the picker`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Pay(data))
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertNull(feature.currentSetup.value)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()

        assertEquals(
            BuyCryptoEvent.PickMethod,
            events.last(),
        )
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
    }

    @Test
    fun `the rate's retry asks for nothing while MoonPay pays`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Pay(data))
        var signIns = 0
        val feature = open(
            moonPay = moonPay(
                check = { signIns++; MoonPayAccess.Connected },
                connect = { signIns++; MoonPaySignIn.SignedIn },
            ),
        )
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        val signedIn = signIns

        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()
        coVerify(exactly = 1) { moonPayPayments.requestOffer(any()) }
        assertEquals(signedIn, signIns)

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.Again))
        settle()
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `a sheet's result during a Continue is ignored`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        val checkedOut = CompletableDeferred<Result<RampCheckout>>()
        coEvery { moonPayPayments.checkout(any(), any()) } coAnswers { checkedOut.await() }
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()
        assertTrue(events.isEmpty())
        assertTrue(feature.continuing.value)

        checkedOut.complete(Result.success(RampCheckout.Pay(data)))
        settle()
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenMoonPay(data.copy(assetLabel = "USDT (TON)", report = report("card", "Mercuryo")))), events)
        assertEquals(false, feature.amountField.value?.editable)
    }

    @Test
    fun `a closed MoonPay frame is no failure`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertTrue(events.isEmpty())
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `another card tapped twice on the closing sheet opens one picker`() {
        val data = MoonPayPaymentData(moonPayQuote(), "card", "order", theme = null)
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Pay(data))
        val feature = open(moonPay = moonPay())
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()
        feature.sendAction(BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
        settle()

        assertEquals(1, events.count { it is BuyCryptoEvent.PickMethod })
    }

    @Test
    fun `opening the screen never shows MoonPay's sign-in, the merchants sell until Continue`() {
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.SignedIn }))
        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()

        assertEquals(0, signIns)
        assertEquals(false, feature.onMoonPay)
    }

    @Test
    fun `the amount screen before MoonPay's sign-in reports MoonPay as headless, as Continue signs in`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }))
        enter(feature, "50")
        settle()

        val shown = sent("deposit_view_ramp_insert_amount").single()
        assertEquals("MoonPay", shown["provider_name"])
        assertEquals("headless", shown["provider_mode"])
    }

    @Test
    fun `Continue with a method the backend lists MoonPay for signs in, keeps the purchase, and the next one pays`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.SignedIn }))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(1, signIns)
        coVerify(exactly = 0) { payments.checkout(any(), any()) }
        coVerify(exactly = 0) { moonPayPayments.checkout(any(), any()) }
        assertTrue(sent("deposit_click_ramp_insert_amount_continue").isEmpty())
        assertEquals(true, feature.onMoonPay)
        assertEquals("50", feature.amount.value.text)
        assertTrue(feature.canContinue.value)

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        coVerify(exactly = 1) { moonPayPayments.checkout(any(), any()) }
        assertEquals(1, sent("deposit_click_ramp_insert_amount_continue").size)
    }

    @Test
    fun `Continue with a method the backend lists no MoonPay for goes to the merchant without a sign-in`() {
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.SignedIn }))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(0, signIns)
        coVerify(exactly = 1) { payments.checkout(any(), any()) }
    }

    @Test
    fun `a closed sign-in changes nothing and the next Continue asks again`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.Closed }))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertTrue(events.isEmpty())
        assertTrue(feature.canContinue.value)
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(2, signIns)
        coVerify(exactly = 0) { payments.checkout(any(), any()) }
        coVerify(exactly = 0) { moonPayPayments.loadMethods(any(), any(), any()) }
    }

    @Test
    fun `a refused sign-in is shown and Continue goes on with the merchants without asking again`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.Refused }))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertEquals(listOf(BuyCryptoEvent.Failed), events)
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(1, signIns)
        coVerify(exactly = 1) { payments.checkout(any(), any()) }
    }

    @Test
    fun `a sign-in that fails otherwise is shown and may be tried again`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        var signIns = 0
        val feature = open(
            moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.Failed(IOException("offline")) }),
        )
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(2, signIns)
        assertEquals(listOf(BuyCryptoEvent.Failed, BuyCryptoEvent.Failed), events)
        coVerify(exactly = 0) { payments.checkout(any(), any()) }
    }

    @Test
    fun `the merchants' limits don't stop a Continue that signs in to MoonPay`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        coEvery { payments.requestOffer(any()) } returns Result.success(RampOffer.OutOfLimits(badge, RampLimitViolation.BelowMin(BigDecimal("30"))))
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }))
        enter(feature, "20")
        settle()

        assertNull(feature.limit.value)
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `MoonPay not selling after the sign-in leaves the merchants, and Continue pays with them`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { signIns++; MoonPaySignIn.SignedIn }))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertEquals(false, feature.onMoonPay)
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(1, signIns)
        coVerify(exactly = 1) { payments.checkout(any(), any()) }
    }

    @Test
    fun `a reload on MoonPay whose session ran out goes to the merchants without a sign-in`() {
        val checks = ArrayDeque(listOf(MoonPayAccess.Connected, MoonPayAccess.SignInRequired))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { checks.removeFirst() }, connect = { signIns++; MoonPaySignIn.SignedIn }))
        assertEquals(true, feature.onMoonPay)

        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(0, signIns)
        assertEquals(false, feature.onMoonPay)
    }

    @Test
    fun `MoonPay unreachable on a reload falls to the merchants, and the next reload is back on MoonPay`() {
        val checks = ArrayDeque(listOf(MoonPayAccess.Connected, MoonPayAccess.Unreachable, MoonPayAccess.Connected))
        val feature = open(moonPay = moonPay(check = { checks.removeFirst() }))
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.failure(IOException("offline"))

        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        assertEquals(false, feature.onMoonPay)

        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayMethod("card")))
        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(true, feature.onMoonPay)
    }

    @Test
    fun `MoonPay unreachable on open is checked again on Reload and on a pick`() {
        val checks = ArrayDeque(listOf(MoonPayAccess.Unreachable, MoonPayAccess.Unreachable, MoonPayAccess.Connected))
        val feature = open(moonPay = moonPay(check = { checks.removeFirst() }))
        assertEquals(false, feature.onMoonPay)

        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        assertEquals(false, feature.onMoonPay)
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertTrue(checks.isEmpty())
        assertEquals(true, feature.onMoonPay)
    }

    @Test
    fun `MoonPay refusing on open is not checked again`() {
        var checks = 0
        val feature = open(moonPay = moonPay(check = { checks++; MoonPayAccess.Unavailable }))

        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals(1, checks)
    }

    @Test
    fun `a pick on MoonPay whose session ran out goes to the merchants, and Continue signs in`() {
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(asset.copy(assetId = TestAssets.ton.id))
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        val checks = ArrayDeque(listOf(MoonPayAccess.Connected, MoonPayAccess.SignInRequired))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { checks.removeFirst() }, connect = { signIns++; MoonPaySignIn.Closed }))

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", null))
        settle()
        assertTrue(checks.isEmpty())
        assertEquals(false, feature.onMoonPay)
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(1, signIns)
    }

    @Test
    fun `connected on the merchants, a pick after the session ran out offers the sign-in on Continue`() {
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)
        coEvery { source.loadAssetDetail(TestAssets.ton.id) } returns Result.success(asset.copy(assetId = TestAssets.ton.id))
        coEvery { payments.loadMethods(TestAssets.ton.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        val checks = ArrayDeque(listOf(MoonPayAccess.Connected, MoonPayAccess.SignInRequired))
        var signIns = 0
        val feature = open(moonPay = moonPay(check = { checks.removeFirst() }, connect = { signIns++; MoonPaySignIn.Closed }))
        assertEquals(false, feature.onMoonPay)

        feature.sendAction(BuyCryptoAction.SelectAsset(TestAssets.ton.id, "USD", "TON", "TON", null))
        settle()
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertTrue(checks.isEmpty())
        assertEquals(1, signIns)
    }

    @Test
    fun `a rate retry's check that ends during a Continue on MoonPay leaves the Continue's sign-in to decide`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        val checked = CompletableDeferred<MoonPayAccess>()
        val signedIn = CompletableDeferred<MoonPaySignIn>()
        var checks = 0
        val feature = open(
            moonPay = moonPay(
                check = {
                    if (checks++ == 0) {
                        MoonPayAccess.Connected
                    } else {
                        checked.await()
                    }
                },
                connect = { signedIn.await() },
            ),
        )
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        checked.complete(MoonPayAccess.SignInRequired)
        settle()
        signedIn.complete(MoonPaySignIn.Refused)
        settle()

        assertEquals(listOf(BuyCryptoEvent.Failed), events)
        assertEquals(false, feature.onMoonPay)
        coVerify(exactly = 0) { moonPayPayments.checkout(any(), any()) }
    }

    @Test
    fun `a Continue on MoonPay that signs in again marks MoonPay connected`() {
        coEvery { moonPayPayments.checkout(any(), any()) } returns Result.success(RampCheckout.Cancelled)
        val checked = CompletableDeferred<MoonPayAccess>()
        val signedIn = CompletableDeferred<MoonPaySignIn>()
        var checks = 0
        val feature = open(
            moonPay = moonPay(
                check = {
                    if (checks++ == 1) {
                        checked.await()
                    } else {
                        MoonPayAccess.Connected
                    }
                },
                connect = { signedIn.await() },
            ),
        )
        enter(feature, "50")
        settle()
        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()
        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        checked.complete(MoonPayAccess.SignInRequired)
        settle()
        signedIn.complete(MoonPaySignIn.SignedIn)
        settle()

        coVerify(exactly = 1) { moonPayPayments.checkout(any(), any()) }
        feature.sendAction(BuyCryptoAction.Reload)
        settle()
        assertEquals(3, checks)
        assertEquals(true, feature.onMoonPay)
    }

    @Test
    fun `retrying the rate on MoonPay whose session ran out checks once and goes to the merchants`() {
        val checks = ArrayDeque(listOf(MoonPayAccess.Connected, MoonPayAccess.SignInRequired))
        var checked = 0
        val feature = open(moonPay = moonPay(check = { checked++; checks.removeFirst() }))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.RetryRate)
        settle()

        assertEquals(2, checked)
        assertEquals(false, feature.onMoonPay)
    }

    @Test
    fun `MoonPay refusing a Continue on MoonPay is shown and the merchants sell`() {
        var signIns = 0
        val feature = open(moonPay = moonPay(connect = { signIns++; MoonPaySignIn.Refused }))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf(BuyCryptoEvent.Failed), events)
        assertEquals(false, feature.onMoonPay)
        coVerify(exactly = 0) { moonPayPayments.checkout(any(), any()) }
        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        assertEquals(1, signIns)
    }

    @Test
    fun `a sign-in failing on a Continue on MoonPay is shown and stays on MoonPay`() {
        val feature = open(moonPay = moonPay(connect = { MoonPaySignIn.Failed(IOException("offline")) }))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(listOf(BuyCryptoEvent.Failed), events)
        assertEquals(true, feature.onMoonPay)
        assertTrue(feature.canContinue.value)
    }

    @Test
    fun `the amount the screen put in stays after the sign-in`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        coEvery { payments.loadAmountRange(any(), any()) } returns Result.success(RampAmountRange(suggested = BigDecimal("30"), max = null))
        coEvery { moonPayPayments.loadAmountRange(any(), any()) } returns Result.success(RampAmountRange(suggested = BigDecimal("20"), max = null))
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }))
        assertEquals("30", feature.amount.value.text)

        feature.sendAction(BuyCryptoAction.Continue)
        settle()

        assertEquals(true, feature.onMoonPay)
        assertEquals("30", feature.amount.value.text)
    }

    private fun moonPay(
        check: suspend () -> MoonPayAccess = { MoonPayAccess.Connected },
        connect: suspend () -> MoonPaySignIn = { MoonPaySignIn.SignedIn },
        buttons: MoonPayButtonPayments? = null,
    ): MoonPayRamp {
        val support = MoonPayMethodSupport(googlePayEnabled = { buttons != null }, deviceSupportsGooglePay = { true })
        return MoonPayRamp(moonPayPayments, check, connect, support, buttons)
    }

    // MoonPay's buy button: each run the screen starts, with its states to drive and the commands its frame got.
    private class ButtonRunStub(val quote: MoonPayBuyQuote, val states: MutableStateFlow<MoonPayPaymentState>, val commands: MutableList<MoonPayFrameCommand>)

    private fun googlePayButtons(runs: MutableList<ButtonRunStub>): MoonPayButtonPayments {
        val quotes = generateSequence(1) { it + 1 }.iterator()
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayMethod("google_pay")))
        coEvery { moonPayPayments.requestOffer(any()) } answers {
            val quote = moonPayQuote().copy(
                signature = "q${quotes.next()}",
                executable = true,
                paymentMethod = MoonPayPaymentMethodReference(type = "google_pay"),
            )
            Result.success((available(firstArg()) as RampOffer.Available).copy(ticket = RampTicket.MoonPay(quote)))
        }
        every { moonPayPayments.buttonPayment(any(), any(), any()) } answers {
            MoonPayPaymentData(firstArg(), secondArg(), thirdArg(), theme = null)
        }
        val interactor = mockk<MoonPayPaymentInteractor> {
            every { pay(any(), any(), any(), any(), any()) } answers {
                val run = ButtonRunStub(firstArg(), MutableStateFlow(MoonPayPaymentState.Starting), mutableListOf())
                runs += run
                val sent = arg<Flow<MoonPayFrameCommand>>(4)
                channelFlow {
                    val frame = launch { sent.collect { run.commands += it } }
                    run.states.transformWhile { state ->
                        emit(state)
                        !state.isFinal
                    }.collect { send(it) }
                    frame.cancel()
                }
            }
        }
        return MoonPayButtonPayments(interactor, CoroutineScope(dispatchers.main + SupervisorJob()))
    }

    @Test
    fun `Google Pay shows MoonPay's button, one per amount, and a refreshed quote of the amount goes into it`() {
        val runs = mutableListOf<ButtonRunStub>()
        val feature = open(moonPay = moonPay(buttons = googlePayButtons(runs)))
        enter(feature, "50")
        settle()

        assertEquals(listOf("q1"), runs.map { it.quote.signature })
        assertEquals(false, feature.payButton.value?.ready)
        assertFalse(feature.canContinue.value)

        runs[0].states.value = MoonPayPaymentState.ButtonReady
        settle()
        assertEquals(true, feature.payButton.value?.enabled)

        pass(RATE_LIFETIME)
        assertEquals(listOf<MoonPayFrameCommand>(MoonPayFrameCommand.SetQuote("q2")), runs[0].commands)
        assertEquals(1, runs.size)

        enter(feature, "60")
        settle()
        assertEquals(2, runs.size)
    }

    @Test
    fun `a button that does not draw within 10 s ends as a failure, told once`() {
        val runs = mutableListOf<ButtonRunStub>()
        val feature = open(moonPay = moonPay(buttons = googlePayButtons(runs)))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        assertEquals(false, feature.payButton.value?.ready)

        pass(10.seconds)

        assertEquals(null, feature.payButton.value)
        assertFalse(feature.canContinue.value)
        assertEquals(1, events.count { it is BuyCryptoEvent.Failed })
    }

    @Test
    fun `a tap opens the sheet with the quote the frame holds, and the closed sheet brings a new button`() {
        val runs = mutableListOf<ButtonRunStub>()
        val feature = open(moonPay = moonPay(buttons = googlePayButtons(runs)))
        val events = collectEvents(feature)
        enter(feature, "50")
        settle()
        runs[0].states.value = MoonPayPaymentState.ButtonReady
        pass(RATE_LIFETIME)

        runs[0].states.value = MoonPayPaymentState.ButtonPressed
        settle()
        val opened = events.filterIsInstance<BuyCryptoEvent.OpenMoonPay>().single()
        assertEquals("q2", opened.data.quote.signature)
        assertEquals(false, feature.payButton.value?.enabled)

        runs[0].states.value = MoonPayPaymentState.Failed(MoonPayPaymentFailure.Cancelled, transactionId = null)
        feature.sendAction(BuyCryptoAction.PaymentEnded(retry = null))
        settle()

        assertEquals(2, runs.size)
        assertTrue(events.none { it is BuyCryptoEvent.Failed })
    }

    @Test
    fun `a picked merchant alone quotes, whatever the others offer`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val feature = open()
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        settle()

        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.transak) }) }
        assertEquals(listOf(ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.transak), quotedBy(firstQuoted()))
    }

    @Test
    fun `a picked merchant that does not sell the new currency is left out, and the next pick forgets it`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } answers { Result.success(listOf(sharedCard(), p2p)) }
        val feature = open()
        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        settle()

        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()
        enter(feature, "50")
        settle()

        coVerify { payments.requestOffer(match { it.fiat == "EUR" && quotedBy(it.method, "EUR") == listOf(ExchangeMerchantSlug.mercuryo) }) }
        feature.sendAction(BuyCryptoAction.SelectFiat("USD"))
        settle()
        enter(feature, "60")
        settle()
        coVerify { payments.requestOffer(match { it.fiat == "USD" && quotedBy(it.method) == listOf(ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.transak) }) }
    }

    @Test
    fun `before a sign-in MoonPay quotes through its widget unless a merchant is picked`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }))
        enter(feature, "50")
        settle()
        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.moonpay) }) }

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.mercuryo))
        settle()
        enter(feature, "60")
        settle()

        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.mercuryo) }) }
        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        coVerify(exactly = 1) { payments.checkout(any(), any()) }
    }

    @Test
    fun `a merchant picked while MoonPay is connected takes over, and picking MoonPay brings it back`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val feature = open(moonPay = moonPay())
        assertEquals(true, feature.onMoonPay)

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.mercuryo))
        settle()
        assertEquals(false, feature.onMoonPay)

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.moonpay))
        settle()
        assertEquals(true, feature.onMoonPay)
    }

    @Test
    fun `a method picked with a provider applies both, and the pick survives a recreation`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        settle()
        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "card", null, null, null, moonPay = false))
        settle()

        val recreated = open(handle = handle)
        enter(recreated, "50")
        settle()

        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.transak) }) }
    }

    @Test
    fun `MoonPay refusing the sign-in its widget stood in for brings back every merchant`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        val feature = open(moonPay = moonPay(check = { MoonPayAccess.SignInRequired }, connect = { MoonPaySignIn.Refused }))
        enter(feature, "50")
        settle()
        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.moonpay) }) }

        feature.sendAction(BuyCryptoAction.Continue)
        settle()
        enter(feature, "60")
        settle()

        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.moonpay) }) }
    }

    @Test
    fun `signed in, a pair MoonPay does not sell leaves every merchant, and MoonPay picked then sells through its widget`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(moonPayListedCard(), p2p))
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(null)
        val feature = open(moonPay = moonPay())
        enter(feature, "50")
        settle()

        coVerify { payments.requestOffer(match { quotedBy(it.method) == listOf(ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.moonpay) }) }
        assertEquals(false, feature.moonPayOffered)
        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.moonpay))
        settle()
        enter(feature, "60")
        settle()
        coVerify { payments.requestOffer(match { it.amount.toPlainString().startsWith("60") && quotedBy(it.method) == listOf(ExchangeMerchantSlug.moonpay) }) }
        coVerify(exactly = 1) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
    }

    @Test
    fun `a merchant pick restored after a process death waits for the MoonPay check, so MoonPay stays on the provider sheet`() {
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val checked = CompletableDeferred<MoonPayAccess>()
        val feature = open(handle = SavedStateHandle(mapOf("buy_crypto_provider" to "mercuryo")), moonPay = moonPay(check = { checked.await() }))
        assertEquals(true, feature.offer.value.loading)

        checked.complete(MoonPayAccess.Connected)
        settle()

        assertEquals(ExchangeMerchantSlug.mercuryo, feature.providers.value.single { it.selected }.id)
        assertEquals(true, feature.moonPayOffered)
    }

    @Test
    fun `signed in, MoonPay selling the pair is offered on the provider sheet`() {
        val feature = open(moonPay = moonPay())

        assertEquals(true, feature.moonPayOffered)
    }

    @Test
    fun `signed in and sold by MoonPay, the line names MoonPay and its own row leads the sheet, checked, with the account's methods`() {
        sellEverywhere()
        val feature = open(moonPay = moonPay())

        assertEquals(listOf(ExchangeMerchantSlug.moonpay, ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.transak), feature.providers.value.map { it.id })
        val own = feature.providers.value.first()
        assertEquals(null, own.imageUrl)
        assertEquals("card", own.subtitle)
        assertEquals(listOf(true, false, false), feature.providers.value.map { it.selected })
        assertEquals(BuyCryptoProviderLine("MoonPay", switchable = true, failed = false), feature.providerLine.value)
        assertEquals(listOf("card"), feature.offer.value.methods?.map { it.id })
    }

    @Test
    fun `with nothing picked the line names the merchant of the shown quote, before a quote the first row, unchecked`() {
        sellEverywhere()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val feature = open()
        assertEquals("Moonpay", feature.providerLine.value?.name)
        assertTrue(feature.providers.value.none { it.selected })

        enter(feature, "50")
        settle()

        assertEquals("Mercuryo", feature.providerLine.value?.name)
        assertEquals(listOf(ExchangeMerchantSlug.mercuryo), feature.providers.value.filter { it.selected }.map { it.id })
        assertEquals(listOf("card", RampPaymentMethod.P2P_ID), feature.offer.value.methods?.map { it.id })
    }

    @Test
    fun `a picked merchant is named at once while its methods load, then the method screen lists only its own`() {
        sellEverywhere()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val feature = open()
        val loaded = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { loaded.await() }

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        scheduler.runCurrent()
        assertEquals("Transak", feature.providerLine.value?.name)
        assertEquals(true, feature.offer.value.loading)

        loaded.complete(Result.success(listOf(sharedCard(), p2p)))
        settle()
        assertEquals(false, feature.offer.value.loading)
        assertEquals(listOf(listOf(ExchangeMerchantSlug.transak)), feature.offer.value.methods?.filter { !it.isP2P }?.map { quotedBy(it) })
        assertEquals(listOf(ExchangeMerchantSlug.transak), feature.providers.value.filter { it.selected }.map { it.id })
    }

    @Test
    fun `a single provider is named without a switch`() {
        val feature = open()

        assertEquals(BuyCryptoProviderLine("Mercuryo", switchable = false, failed = false), feature.providerLine.value)
    }

    @Test
    fun `providers that fail to load leave a line to retry, each failed retry is counted and a good one fills the sheet`() {
        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        val feature = open()
        assertEquals(BuyCryptoProviderLine(null, switchable = true, failed = true), feature.providerLine.value)

        feature.sendAction(BuyCryptoAction.RetryProviders)
        settle()
        assertEquals(1, feature.providerFailures.value)

        coEvery { exchange.getMerchants(TestWallet.ID) } returns listOf(merchant(ExchangeMerchantSlug.mercuryo))
        feature.sendAction(BuyCryptoAction.RetryProviders)
        settle()

        assertEquals(BuyCryptoProviderLine("Mercuryo", switchable = false, failed = false), feature.providerLine.value)
        assertEquals(1, feature.providerFailures.value)
    }

    @Test
    fun `after a retry, a later load of the providers that fails on its own is not a failed retry`() {
        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        val feature = open()
        coEvery { exchange.getMerchants(TestWallet.ID) } returns listOf(merchant(ExchangeMerchantSlug.mercuryo))
        feature.sendAction(BuyCryptoAction.RetryProviders)
        settle()

        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        settle()

        assertEquals(true, feature.providerLine.value?.failed)
        assertEquals(0, feature.providerFailures.value)
    }

    @Test
    fun `tapping the merchant of the quote pins it with the method it quotes`() {
        sellEverywhere()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "card", null, null, null, moonPay = false))
        enter(feature, "50")
        settle()

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.mercuryo))
        settle()

        assertEquals("mercuryo", handle.get<String>("buy_crypto_provider"))
        assertEquals("card", handle.get<String>("buy_crypto_method"))
    }

    @Test
    fun `tapping MoonPay while it sells by default and a reload runs changes nothing`() {
        sellEverywhere()
        val handle = SavedStateHandle()
        val feature = open(handle = handle, moonPay = moonPay())
        val loaded = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { loaded.await() }

        feature.sendAction(BuyCryptoAction.Reload)
        scheduler.runCurrent()
        assertEquals(listOf(ExchangeMerchantSlug.moonpay), feature.providers.value.filter { it.selected }.map { it.id })
        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.moonpay))
        loaded.complete(Result.success(listOf(moonPayMethod("card"))))
        settle()

        assertNull(handle.get<String>("buy_crypto_provider"))
        coVerify(exactly = 2) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
    }

    @Test
    fun `a retry of the providers that another pair overtakes is not a failed retry`() {
        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        val feature = open()
        val answer = CompletableDeferred<List<ExchangeMerchantInfo>>()
        coEvery { exchange.getMerchants(TestWallet.ID) } coAnswers { answer.await() }

        feature.sendAction(BuyCryptoAction.RetryProviders)
        scheduler.runCurrent()
        feature.sendAction(BuyCryptoAction.SelectFiat("EUR"))
        scheduler.runCurrent()
        answer.completeExceptionally(IOException("offline"))
        settle()

        assertEquals(true, feature.providerLine.value?.failed)
        assertEquals(0, feature.providerFailures.value)
    }

    @Test
    fun `while «Buy crypto» loads the provider line waits and a retry it overtakes is not a failed retry`() {
        sellEverywhere()
        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        val feature = open()
        val answer = CompletableDeferred<List<ExchangeMerchantInfo>>()
        coEvery { exchange.getMerchants(TestWallet.ID) } coAnswers { answer.await() }
        val loaded = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { loaded.await() }

        feature.sendAction(BuyCryptoAction.RetryProviders)
        feature.sendAction(BuyCryptoAction.Reload)
        scheduler.runCurrent()
        assertTrue(feature.providerLine.value?.switchable != true)
        answer.completeExceptionally(IOException("offline"))
        loaded.complete(Result.success(listOf(card, p2p)))
        settle()

        assertEquals(0, feature.providerFailures.value)
    }

    @Test
    fun `a provider pick and a retry of the providers that arrive while «Buy crypto» loads are dropped`() {
        sellEverywhere()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        coEvery { exchange.getMerchants(TestWallet.ID) } throws IOException("offline")
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        val loaded = CompletableDeferred<Result<List<RampPaymentMethod>?>>()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } coAnswers { loaded.await() }

        feature.sendAction(BuyCryptoAction.Reload)
        scheduler.runCurrent()
        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        feature.sendAction(BuyCryptoAction.RetryProviders)
        loaded.complete(Result.success(listOf(sharedCard(), p2p)))
        settle()

        assertNull(handle.get<String>("buy_crypto_provider"))
        coVerify(exactly = 1) { exchange.getMerchants(TestWallet.ID) }
    }

    @Test
    fun `a card removed on the method screen stays out of the methods, and MoonPay's row names methods, not saved cards`() {
        sellEverywhere()
        coEvery { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns
            Result.success(listOf(moonPayMethod("card"), savedCard("a"), savedCard("b")))
        val feature = open(moonPay = moonPay())

        feature.sendAction(BuyCryptoAction.CardsRemoved(setOf("b")))
        settle()

        assertEquals(listOf("card", "card:a"), feature.offer.value.methods.map { it.id })
        assertEquals("card", feature.providers.value.first { it.isMoonPay }.subtitle)
    }

    @Test
    fun `tapping MoonPay while it sells by default changes nothing`() {
        sellEverywhere()
        val feature = open(moonPay = moonPay())

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.moonpay))
        settle()

        coVerify(exactly = 1) { moonPayPayments.loadMethods(TestAssets.usdtTon.id, any(), any()) }
        assertEquals(listOf(ExchangeMerchantSlug.moonpay), feature.providers.value.filter { it.selected }.map { it.id })
    }

    @Test
    fun `another provider starts from its own default method, not the id picked at the last one`() {
        sellEverywhere()
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.success(listOf(sharedCard(), p2p))
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        feature.sendAction(BuyCryptoAction.SelectMethod("card", "USD", "card", null, null, null, moonPay = false))
        settle()

        feature.sendAction(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.transak))
        settle()

        assertNull(handle.get<String>("buy_crypto_method"))
    }

    @Test
    fun `a first load that fails is a failure the method screen can retry, not a load that never ends`() {
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } returns Result.failure(IOException("offline"))
        coEvery { source.findDefaultAsset(any()) } returns Result.failure(IOException("offline"))
        val feature = open()

        assertEquals(false, feature.offer.value.loading)
        assertEquals(true, feature.offer.value.failed)
    }

    @Test
    fun `a reload that fails while «Buy crypto» is off screen leaves no toast for its return`() {
        val feature = open()
        val events = collectEvents(feature)
        feature.sendAction(BuyCryptoAction.SetVisible(false))
        coEvery { payments.loadMethods(TestAssets.usdtTon.id, any(), any()) } returns Result.failure(IOException("offline"))

        feature.sendAction(BuyCryptoAction.Reload)
        settle()

        assertEquals(true, feature.offer.value.failed)
        assertTrue(events.none { it is BuyCryptoEvent.Failed })
    }

    // Every merchant, MoonPay's widget among them, sells the card in USD.
    private fun sellEverywhere() {
        val sold = rampMethod(ExchangePaymentMethodType.card).copy(
            providers = listOf(ExchangeMerchantSlug.moonpay, ExchangeMerchantSlug.mercuryo, ExchangeMerchantSlug.transak)
                .map { RampProvider(it, "USD", ExchangeLimits()) },
        )
        val everywhere = asset.copy(methods = listOf(sold))
        coEvery { source.loadAssetDetail(TestAssets.usdtTon.id) } returns Result.success(everywhere)
        coEvery { source.resolveFiat(everywhere, any()) } answers { secondArg() }
    }

    private fun sharedCard(): RampPaymentMethod {
        val method = rampMethod(ExchangePaymentMethodType.card).copy(
            providers = listOf(
                RampProvider(ExchangeMerchantSlug.mercuryo, "USD", ExchangeLimits()),
                RampProvider(ExchangeMerchantSlug.transak, "USD", ExchangeLimits()),
                RampProvider(ExchangeMerchantSlug.mercuryo, "EUR", ExchangeLimits()),
            ),
        )
        return card.copy(details = RampPaymentMethod.Details.Merchant(method))
    }

    private fun quotedBy(method: RampPaymentMethod, fiat: String = "USD"): List<ExchangeMerchantSlug> {
        return (method.details as RampPaymentMethod.Details.Merchant).method.providers.filter { it.fiat == fiat }.map { it.merchant }
    }

    private fun firstQuoted(): RampPaymentMethod {
        val requests = mutableListOf<RampQuoteRequest>()
        coVerify { payments.requestOffer(capture(requests)) }
        return requests.first().method
    }

    // A card the backend lets MoonPay's own widget take in USD, next to Mercuryo.
    private fun moonPayListedCard(): RampPaymentMethod {
        val method = rampMethod(ExchangePaymentMethodType.card).let {
            it.copy(providers = it.providers + RampProvider(ExchangeMerchantSlug.moonpay, "USD", ExchangeLimits()))
        }
        return card.copy(details = RampPaymentMethod.Details.Merchant(method))
    }

    private fun open(
        assetId: String? = null,
        preferredCurrency: String? = null,
        handle: SavedStateHandle = SavedStateHandle(),
        moonPay: MoonPayRamp? = null,
    ): BuyCryptoFeature {
        val feature = BuyCryptoFeature(
            data = BuyCryptoData(TestWallet.ID, assetId, preferredCurrency, DepositFlowFrom.WalletScreen, UtmParameters.Empty),
            source = source,
            merchants = payments,
            moonPay = moonPay,
            exchange = exchange,
            settingsRepository = settingsRepository,
            appLifecycle = appLifecycle,
            savedStateHandle = handle,
            networkMonitor = networkMonitor,
            timeSource = timeSource,
        )
        settle()
        return feature
    }

    private fun enter(feature: BuyCryptoFeature, text: String) {
        feature.sendAction(BuyCryptoAction.SetAmount(text))
        scheduler.runCurrent()
    }

    // Past the quote's debounce, short of the rate's timer: the timer never lets the scheduler idle.
    private fun settle() {
        pass(1.seconds)
    }

    private fun report(paymentMethod: String, provider: String): BuyCryptoReport {
        val purchase = BuyCryptoPurchase(TestAssets.usdtTon.id, "USD", paymentMethod, provider, DepositFlowProviderMode.Headless)
        return BuyCryptoReport(DepositFlowFrom.WalletScreen, UtmParameters.Empty, purchase)
    }

    private fun sent(eventName: String): List<Map<String, Any>> = tracked.filter { it.first == eventName }.map { it.second }

    private fun pass(duration: Duration) {
        scheduler.advanceTimeBy(duration)
        scheduler.runCurrent()
    }

    private fun collectEvents(feature: BuyCryptoFeature): List<BuyCryptoEvent> {
        val events = mutableListOf<BuyCryptoEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private fun available(request: RampQuoteRequest, lifetime: Duration? = null): RampOffer {
        return RampOffer.Available(request, receive = BigDecimal("49"), rate = BigDecimal("0.98"), lifetime = lifetime, badgeUrl = badge, ticket = ticket)
    }

    private fun moonPayQuote(): MoonPayBuyQuote {
        return MoonPayBuyQuote(
            source = MoonPayMoney(amount = "50", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(amount = "49", asset = MoonPayAsset(code = "usdt_ton")),
            signature = "signature",
        )
    }

    private fun savedCard(id: String): RampPaymentMethod {
        return RampPaymentMethod(
            id = "card:$id",
            title = "Debit card",
            subtitle = "Visa •••• 4148",
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.MoonPay("card", TestAssets.usdtTon.id, MoonPayAsset(code = "usdt_ton"), cardId = id, cardType = "card"),
            card = RampSavedCard(id, "debit", "visa", "4148", 12, 2030, active = true, removable = true),
        )
    }

    private fun moonPayMethod(type: String): RampPaymentMethod {
        return RampPaymentMethod(
            id = type,
            title = type,
            subtitle = null,
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.MoonPay(type, TestAssets.usdtTon.id, MoonPayAsset(code = "usdt_ton")),
        )
    }

    private fun method(type: ExchangePaymentMethodType): RampPaymentMethod {
        return RampPaymentMethod(
            id = type.value,
            title = type.value,
            subtitle = null,
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.Merchant(rampMethod(type)),
        )
    }

    private fun rampMethod(type: ExchangePaymentMethodType): RampMethod {
        return RampMethod(
            type = type,
            name = type.value,
            image = "",
            providers = listOf(RampProvider(ExchangeMerchantSlug.mercuryo, "USD", ExchangeLimits())),
        )
    }

    companion object {
        private val RATE_LIFETIME = 12.seconds

        @JvmStatic
        @BeforeClass
        fun stubPlatformNumberFormat() {
            PlatformNumberFormat.stub()
        }
    }
}

private val GROUPING = Formatter.defaultOptions.groupingSeparator.orEmpty()

// The method screen would list MoonPay's own methods.
private val BuyCryptoFeature.onMoonPay: Boolean?
    get() = offer.value.takeIf { !it.loading }?.methods?.any { it.isMoonPay }

// MoonPay's own row, not its widget among the merchants: only the merchants carry an image.
private val BuyCryptoFeature.moonPayOffered: Boolean
    get() = providers.value.any { it.isMoonPay && it.imageUrl == null }

private fun merchant(slug: ExchangeMerchantSlug): ExchangeMerchantInfo {
    return ExchangeMerchantInfo(
        id = slug.value,
        title = slug.value.replaceFirstChar { it.uppercase() },
        description = "",
        image = "https://img/${slug.value}",
        fee = "",
        buttons = emptyList(),
    )
}

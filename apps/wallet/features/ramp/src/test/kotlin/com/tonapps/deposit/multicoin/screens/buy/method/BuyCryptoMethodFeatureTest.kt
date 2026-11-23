package com.tonapps.deposit.multicoin.screens.buy.method

import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import androidx.lifecycle.SavedStateHandle
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.MoonPayRamp
import com.tonapps.deposit.multicoin.domain.MoonPaySignIn
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampSavedCard
import com.tonapps.deposit.multicoin.domain.RampSource
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoAction
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoCard
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoOffer
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoOfferState
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderLine
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderRow
import com.tonapps.wallet.testing.BaseUnitTest
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class BuyCryptoMethodFeatureTest : BaseUnitTest() {

    private val card = method(ExchangePaymentMethodType.card)
    private val p2p = method(ExchangePaymentMethodType.p2p)

    private val source = mockk<RampSource> {
        coEvery { createP2PSessionUrl("usdt", "EUR") } returns Result.success("https://p2p")
    }
    private val newCard = RampPaymentMethod(
        id = "card",
        title = "Debit Card",
        subtitle = null,
        iconUrl = null,
        badge = null,
        details = RampPaymentMethod.Details.MoonPay("card", "usdt", MoonPayAsset(code = "usdt_ton")),
    )

    private val moonPayPayments = mockk<RampPayments>()
    private val moonPay = MoonPayRamp(
        moonPayPayments,
        check = { MoonPayAccess.Connected },
        connect = { MoonPaySignIn.SignedIn },
        methodSupport = MoonPayMethodSupport(googlePayEnabled = { false }, deviceSupportsGooglePay = { true }),
        buttons = null,
    )

    private val buy = FakeOffer(offer(listOf(card, p2p)))

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
    fun `the picker reports its methods once per opening, a pick and an opened market`() {
        val handle = SavedStateHandle()
        val feature = open(handle = handle)
        open(handle = handle)

        pick(feature, "card")
        pick(feature, RampPaymentMethod.P2P_ID)

        val picker = mapOf(
            "from" to "wallet_screen",
            "add_funds_option" to "buy_with_fiat",
            "deposit_screen" to "buy_crypto",
            "buy_asset" to "usdt",
            "sell_asset" to "EUR",
        )
        assertEquals(listOf(picker + mapOf("available_options" to "card,p2p")), sent("deposit_view_fiat_payment_method"))
        assertEquals(
            listOf(picker + mapOf("payment_method" to "card"), picker + mapOf("payment_method" to "p2p")),
            sent("deposit_click_fiat_payment_method"),
        )
        assertEquals(1, sent("deposit_continue_to_p2p_market").size)
    }

    @Test
    fun `the picker is reported only once the methods of «Buy crypto» land`() {
        buy.offer.value = offer(emptyList(), loading = true)
        open()
        assertTrue(sent("deposit_view_fiat_payment_method").isEmpty())

        buy.offer.value = offer(listOf(card, p2p))
        dispatchers.scheduler.runCurrent()

        assertEquals(1, sent("deposit_view_fiat_payment_method").size)
    }

    @Test
    fun `a picked method goes to «Buy crypto» and closes the picker, a second tap before it closes is no second pick`() {
        val feature = open()
        val events = collectEvents(feature)

        pick(feature, "card")
        pick(feature, "card")

        assertEquals(listOf<BuyCryptoAction>(BuyCryptoAction.SelectMethod("card", "EUR", "card", null, null, null, moonPay = false)), buy.sent)
        assertEquals(1, sent("deposit_click_fiat_payment_method").size)
        assertEquals(listOf(BuyCryptoMethodEvent.Picked), events)
    }

    @Test
    fun `a saved MoonPay card is reported as a saved card`() {
        buy.offer.value = offer(listOf(newCard, saved("valid", 12, 2030)))
        val feature = open()

        pick(feature, "card:valid")

        assertEquals("saved_card", sent("deposit_click_fiat_payment_method").single()["payment_method"])
        assertEquals("card,saved_card", sent("deposit_view_fiat_payment_method").single()["available_options"])
    }

    @Test
    fun `opening covers «Buy crypto», so its failures are told here and not on its return`() {
        open()

        assertEquals(listOf(false), buy.visibility)
    }

    @Test
    fun `the method «Buy crypto» pays with is marked`() {
        buy.offer.value = offer(listOf(card, p2p), selected = "card")
        val feature = open()

        assertEquals(listOf(true, false), feature.methods.value.map { it.selected })
    }

    @Test
    fun `P2P opens the market instead of a pick`() {
        val feature = open()
        val events = collectEvents(feature)

        pick(feature, RampPaymentMethod.P2P_ID)

        assertEquals(listOf<BuyCryptoMethodEvent>(BuyCryptoMethodEvent.OpenUrl("https://p2p")), events)
        assertTrue(buy.sent.isEmpty())
    }

    @Test
    fun `a P2P session that fails is reported and can be tried again`() {
        coEvery { source.createP2PSessionUrl("usdt", "EUR") } returns Result.failure(IOException("offline"))
        val feature = open()
        val events = collectEvents(feature)

        pick(feature, RampPaymentMethod.P2P_ID)
        coEvery { source.createP2PSessionUrl("usdt", "EUR") } returns Result.success("https://p2p")
        pick(feature, RampPaymentMethod.P2P_ID)

        assertEquals(listOf(BuyCryptoMethodEvent.P2PFailed, BuyCryptoMethodEvent.OpenUrl("https://p2p")), events)
    }

    @Test
    fun `an opened market is not opened again`() {
        val feature = open()
        val events = collectEvents(feature)

        pick(feature, RampPaymentMethod.P2P_ID)
        pick(feature, RampPaymentMethod.P2P_ID)

        assertEquals(listOf<BuyCryptoMethodEvent>(BuyCryptoMethodEvent.OpenUrl("https://p2p")), events)
        coVerify(exactly = 1) { source.createP2PSessionUrl("usdt", "EUR") }
    }

    @Test
    fun `the market shows it is opening`() {
        val session = CompletableDeferred<Result<String>>()
        coEvery { source.createP2PSessionUrl("usdt", "EUR") } coAnswers { session.await() }
        val feature = open()

        pick(feature, RampPaymentMethod.P2P_ID)
        assertTrue(feature.openingP2P.value)

        session.complete(Result.success("https://p2p"))
        dispatchers.scheduler.runCurrent()
        assertFalse(feature.openingP2P.value)
    }

    @Test
    fun `while «Buy crypto» loads the list waits, a failed load is shown and Retry asks «Buy crypto» to load again`() {
        buy.offer.value = offer(listOf(card), loading = true)
        val feature = open()
        assertTrue(feature.loading.value)
        assertTrue(feature.methods.value.isEmpty())

        buy.offer.value = offer(emptyList(), failed = true)
        dispatchers.scheduler.runCurrent()
        assertFalse(feature.loading.value)
        assertTrue(feature.failed.value)

        feature.sendAction(BuyCryptoMethodAction.Reload)
        dispatchers.scheduler.runCurrent()

        assertEquals(listOf<BuyCryptoAction>(BuyCryptoAction.Reload), buy.sent)
    }

    @Test
    fun `a picked provider and a retry of the providers go to «Buy crypto», which the sheet and the line follow`() {
        val feature = open()

        feature.sendAction(BuyCryptoMethodAction.PickProvider(ExchangeMerchantSlug.mercuryo))
        feature.sendAction(BuyCryptoMethodAction.RetryProviders)
        dispatchers.scheduler.runCurrent()
        val rows = listOf(BuyCryptoProviderRow(ExchangeMerchantSlug.mercuryo, "Mercuryo", "Card", null, selected = true))
        buy.providers.value = rows
        buy.providerLine.value = BuyCryptoProviderLine("Mercuryo", switchable = false, failed = false)
        dispatchers.scheduler.runCurrent()

        assertEquals(listOf(BuyCryptoAction.SelectProvider(ExchangeMerchantSlug.mercuryo), BuyCryptoAction.RetryProviders), buy.sent)
        assertEquals(rows, feature.providers.value)
        assertEquals("Mercuryo", feature.providerLine.value?.name)
    }

    @Test
    fun `each failed retry of the providers is shown once`() {
        buy.providerLine.value = BuyCryptoProviderLine(null, switchable = true, failed = true)
        val feature = open()
        val events = collectEvents(feature)

        buy.providerFailures.value = 1
        dispatchers.scheduler.runCurrent()
        buy.providerLine.value = null
        dispatchers.scheduler.runCurrent()
        buy.providerLine.value = BuyCryptoProviderLine("Mercuryo", switchable = true, failed = false)
        dispatchers.scheduler.runCurrent()

        assertEquals(listOf<BuyCryptoMethodEvent>(BuyCryptoMethodEvent.ProvidersFailed), events)
    }

    @Test
    fun `saved cards show their icon and how long they last, one MoonPay no longer takes cannot be picked`() {
        buy.offer.value = offer(
            listOf(saved("valid", 10, 2028), saved("soon", 12, 26), saved("expired", 10, 2026), saved("blocked", null, null, active = false), newCard),
            selected = "card:valid",
        )

        val rows = open().methods.value

        assertEquals(
            listOf(
                BuyCryptoCardExpiry("10/28", BuyCryptoCardExpiry.State.Valid),
                BuyCryptoCardExpiry("12/26", BuyCryptoCardExpiry.State.Soon),
                BuyCryptoCardExpiry("10/26", BuyCryptoCardExpiry.State.Expired),
                BuyCryptoCardExpiry(null, BuyCryptoCardExpiry.State.Expired),
                null,
            ),
            rows.map { it.card?.expiry },
        )
        assertEquals(BuyCryptoCard(BuyCryptoCard.Logo.Visa, "4148"), rows.first().card?.icon)
        assertEquals(listOf(true, true, true, false, true), rows.map { it.enabled })
        assertEquals(listOf(true, false, false, false, false), rows.map { it.selected })
    }

    @Test
    fun `a removed card leaves the list at once, «Buy crypto» learns of it, and the mark moves on`() {
        buy.offer.value = offer(listOf(saved("valid", 10, 2028), saved("soon", 12, 26), newCard), selected = "card:valid")
        coEvery { moonPayPayments.removeCard("valid") } returns Result.success(Unit)
        val feature = open()
        val events = collectEvents(feature)

        feature.sendAction(BuyCryptoMethodAction.RemoveCard("valid"))
        dispatchers.scheduler.runCurrent()

        assertEquals(listOf("card:soon", "card"), feature.methods.value.map { it.id })
        assertEquals(listOf(true, false), feature.methods.value.map { it.selected })
        assertEquals(listOf<BuyCryptoAction>(BuyCryptoAction.CardsRemoved(setOf("valid"))), buy.sent)
        assertEquals(listOf<BuyCryptoMethodEvent>(BuyCryptoMethodEvent.CardRemoved), events)
    }

    @Test
    fun `a card being removed cannot be picked, and one that fails to be removed comes back with its menu`() {
        buy.offer.value = offer(listOf(saved("valid", 10, 2028), newCard))
        val removal = CompletableDeferred<Result<Unit>>()
        coEvery { moonPayPayments.removeCard("valid") } coAnswers { removal.await() }
        val feature = open()
        val events = collectEvents(feature)

        feature.sendAction(BuyCryptoMethodAction.RemoveCard("valid"))
        dispatchers.scheduler.runCurrent()
        assertEquals(false, feature.methods.value.first().card?.removable)
        assertEquals(false, feature.methods.value.first().enabled)
        pick(feature, "card:valid")
        removal.complete(Result.failure(IOException("offline")))
        dispatchers.scheduler.runCurrent()

        assertEquals(true, feature.methods.value.first().card?.removable)
        assertEquals(true, feature.methods.value.first().enabled)
        assertTrue(buy.sent.isEmpty())
        assertEquals(listOf<BuyCryptoMethodEvent>(BuyCryptoMethodEvent.CardRemovalFailed), events)
    }

    private class FakeOffer(initial: BuyCryptoOfferState) : BuyCryptoOffer {
        override val offer = MutableStateFlow(initial)
        override val providers = MutableStateFlow(emptyList<BuyCryptoProviderRow>())
        override val providerLine = MutableStateFlow<BuyCryptoProviderLine?>(null)
        override val providerFailures = MutableStateFlow(0)
        val sent = mutableListOf<BuyCryptoAction>()
        val visibility = mutableListOf<Boolean>()

        override fun sendAction(action: BuyCryptoAction) {
            if (action is BuyCryptoAction.SetVisible) {
                visibility += action.visible
            } else {
                sent += action
            }
        }
    }

    private fun offer(
        methods: List<RampPaymentMethod>,
        selected: String? = null,
        loading: Boolean = false,
        failed: Boolean = false,
    ): BuyCryptoOfferState {
        return BuyCryptoOfferState("usdt", "EUR", loading, failed, methods, selected)
    }

    private fun open(handle: SavedStateHandle = SavedStateHandle()): BuyCryptoMethodFeature {
        val feature = BuyCryptoMethodFeature(
            BuyCryptoMethodData("wallet", DepositFlowFrom.WalletScreen, UtmParameters.Empty),
            buy,
            source,
            moonPay,
            savedStateHandle = handle,
            today = { LocalDate.of(2026, 11, 7) },
        )
        dispatchers.scheduler.runCurrent()
        return feature
    }

    private fun sent(eventName: String): List<Map<String, Any>> = tracked.filter { it.first == eventName }.map { it.second }

    private fun pick(feature: BuyCryptoMethodFeature, id: String) {
        feature.sendAction(BuyCryptoMethodAction.Pick(id))
        dispatchers.scheduler.runCurrent()
    }

    private fun collectEvents(feature: BuyCryptoMethodFeature): List<BuyCryptoMethodEvent> {
        val events = mutableListOf<BuyCryptoMethodEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private fun saved(id: String, month: Int?, year: Int?, active: Boolean = true): RampPaymentMethod {
        return newCard.copy(
            id = "card:$id",
            title = "Debit card",
            subtitle = "Visa •••• 4148",
            details = RampPaymentMethod.Details.MoonPay("card", "usdt", MoonPayAsset(code = "usdt_ton"), cardId = id, cardType = "card"),
            card = RampSavedCard(id, "debit", "visa", "4148", month, year, active = active, removable = true),
        )
    }

    private fun method(type: ExchangePaymentMethodType): RampPaymentMethod {
        return RampPaymentMethod(
            id = type.value,
            title = type.value,
            subtitle = null,
            iconUrl = null,
            badge = null,
            details = RampPaymentMethod.Details.Merchant(
                RampMethod(
                    type = type,
                    name = type.value,
                    image = "",
                    providers = listOf(RampProvider(ExchangeMerchantSlug.mercuryo, "EUR", ExchangeLimits())),
                ),
            ),
        )
    }
}
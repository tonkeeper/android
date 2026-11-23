package com.tonapps.deposit.multicoin.screens.buy

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.ButtonPhase
import com.tonapps.deposit.multicoin.analytics.BuyCryptoPurchase
import com.tonapps.deposit.multicoin.analytics.BuyCryptoReport
import com.tonapps.deposit.multicoin.analytics.BuyCryptoTrack
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowProviderMode
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.RampAmountRange
import com.tonapps.deposit.multicoin.domain.RampCheckout
import com.tonapps.deposit.multicoin.domain.RampLimitViolation
import com.tonapps.deposit.multicoin.domain.RampSavedCard
import com.tonapps.deposit.multicoin.domain.RampOffer
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampQuoteRequest
import com.tonapps.deposit.multicoin.domain.RampTicket
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.AmountState
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Command
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Phase
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Session
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Setup
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.SetupRequest
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Step
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentRetry
import io.exchangeapi.models.ExchangeMerchantSlug
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// The checkout phase, state by event: what the session becomes and what the screen is told.
class BuyCryptoReducerTest {

    private val moonPay = mockk<RampPayments>()
    private val buttonIds = generateSequence(1) { it + 1 }.map { "b$it" }.iterator()
    private val reducer = BuyCryptoReducer(moonPay, DepositFlowFrom.WalletScreen, UtmParameters.Empty, buttonIds::next)
    private val method = RampPaymentMethod(
        id = "card:visa",
        title = "Debit card",
        subtitle = "Visa •••• 4148",
        iconUrl = null,
        badge = null,
        details = RampPaymentMethod.Details.MoonPay("card", "usdt", MoonPayAsset(code = "usdt_ton"), cardId = "visa", cardType = "card"),
        card = RampSavedCard("visa", "debit", "visa", "4148", 10, 2028, active = true, removable = true),
    )
    private val newCard = method.copy(id = "card", subtitle = null, details = RampPaymentMethod.Details.MoonPay("card", "usdt", MoonPayAsset(code = "usdt_ton")), card = null)
    private val googlePay = newCard.copy(id = "google_pay", title = "Google Pay", details = RampPaymentMethod.Details.MoonPay("google_pay", "usdt", MoonPayAsset(code = "usdt_ton")))

    private val asset = RampAssetDetail(
        assetId = "usdt",
        symbol = "USDT",
        decimals = 6,
        image = "",
        networkName = "TON",
        networkImage = null,
        methods = emptyList(),
        extraIdRequired = false,
        extraIdName = null,
    )
    private val setup = Setup(asset, "USD", method, moonPay)
    private val payment = MoonPayPaymentData(
        quote = MoonPayBuyQuote(
            source = MoonPayMoney(amount = "50", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(amount = "49", asset = MoonPayAsset(code = "usdt_ton")),
            signature = "signature",
        ),
        paymentTitle = "Debit card",
        externalTransactionId = "order",
        theme = null,
    )

    @Test
    fun `idle with an offer continues with that offer`() {
        val idle = quoted()

        val step = reduce(idle, BuyCryptoAction.Continue)

        val phase = step.session.phase as Phase.Continuing
        assertSame(idle.quote.offer, phase.offer)
        assertTrue(phase.onMoonPay)
        assertFalse(step.session.canContinue)
        assertTrue(step.events.isEmpty())
    }

    @Test
    fun `idle without an offer does not continue`() {
        val loaded = loaded()

        assertEquals(loaded, reduce(loaded, BuyCryptoAction.Continue).session)
    }

    @Test
    fun `while continuing a tap, a typed amount, a requote and a sheet's result change nothing`() {
        val continuing = continuing()

        listOf(
            BuyCryptoAction.Continue,
            BuyCryptoAction.SetAmount("60"),
            BuyCryptoAction.Requote,
            BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard),
        ).forEach { action ->
            val step = reduce(continuing, action)
            assertEquals(continuing, step.session)
            assertTrue(step.events.isEmpty())
        }
    }

    @Test
    fun `a Continue ending in Pay opens the sheet and holds the screen`() {
        val step = reducer.reduce(continuing(), Command.ContinueEnded(1, RampCheckout.Pay(payment)))

        assertEquals(Phase.Paying, step.session.phase)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenMoonPay(
                payment.copy(
                    assetLabel = "USDT (TON)",
                    report = BuyCryptoReport(
                        DepositFlowFrom.WalletScreen,
                        UtmParameters.Empty,
                        BuyCryptoPurchase("usdt", "USD", "saved_card", "MoonPay", DepositFlowProviderMode.Headless),
                    ),
                ),
            )), step.events)
        assertFalse(step.session.canContinue)
    }

    @Test
    fun `MoonPay's button reports its tap and handover as headless`() {
        val quoted = googlePayQuoted("q1")
        val id = quoted.button!!.id
        val pressed = reducer.reduce(quoted, Command.ButtonPressed(id, payment.copy(quote = gpQuote("q1"), externalTransactionId = id)))

        val purchases = pressed.tracks.map {
            when (it) {
                is BuyCryptoTrack.Continued -> it.purchase
                is BuyCryptoTrack.ToProvider -> it.purchase
                else -> error("unexpected $it")
            }
        }
        assertEquals(listOf(DepositFlowProviderMode.Headless, DepositFlowProviderMode.Headless), purchases.map { it.mode })
    }

    @Test
    fun `a Continue opening a widget holds the screen for good`() {
        val ordered = reducer.reduce(continuing(), Command.ContinueEnded(1, RampCheckout.OpenUrl("https://widget")))

        assertEquals(Phase.Ordered, ordered.session.phase)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.OpenUrl("https://widget")), ordered.events)
        assertEquals(ordered.session, reduce(ordered.session, BuyCryptoAction.Continue).session)
        assertEquals(ordered.session, reduce(ordered.session, BuyCryptoAction.PaymentEnded(retry = null)).session)
    }

    @Test
    fun `a card added or a challenge ended loads again, checking MoonPay's connection first`() {
        val continuing = continuing()

        val step = reducer.reduce(continuing, Command.ContinueEnded(1, RampCheckout.Reload))

        assertEquals(Phase.Idle, step.session.phase)
        assertTrue(step.session.loading)
        assertTrue(step.session.recheck)
        assertEquals(continuing.reload + 1, step.session.reload)
        assertTrue(step.events.isEmpty())
    }

    @Test
    fun `a failed challenge is shown and loads again`() {
        val step = reducer.reduce(continuing(), Command.ContinueEnded(1, RampCheckout.ReloadAfterFailure))

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), step.events)
        assertTrue(step.session.loading)
        assertEquals(Phase.Idle, step.session.phase)
    }

    @Test
    fun `a cancelled Continue is back to idle quietly, a failed one with a toast`() {
        val cancelled = reducer.reduce(continuing(), Command.ContinueEnded(1, RampCheckout.Cancelled))
        val failed = reducer.reduce(continuing(), Command.ContinueEnded(1, checkout = null))

        assertTrue(cancelled.session.canContinue)
        assertTrue(cancelled.events.isEmpty())
        assertTrue(failed.session.canContinue)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), failed.events)
    }

    @Test
    fun `the end of another Continue changes nothing`() {
        val continuing = continuing()

        assertEquals(continuing, reducer.reduce(continuing, Command.ContinueEnded(2, RampCheckout.Pay(payment))).session)
    }

    @Test
    fun `back from the sheet the screen is idle with the same quote`() {
        val paying = paying()

        listOf(MoonPayPaymentRetry.Again, MoonPayPaymentRetry.ChangeAmount, null).forEach { retry ->
            val step = reduce(paying, BuyCryptoAction.PaymentEnded(retry))
            assertEquals(Phase.Idle, step.session.phase)
            assertEquals(paying.quote, step.session.quote)
            assertTrue(step.session.canContinue)
            assertTrue(step.events.isEmpty())
        }
    }

    @Test
    fun `another card or another method from the sheet opens the method picker`() {
        listOf(MoonPayPaymentRetry.AnotherCard, MoonPayPaymentRetry.AnotherMethod).forEach { retry ->
            val step = reduce(paying(), BuyCryptoAction.PaymentEnded(retry))

            assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.PickMethod), step.events)
        }
    }

    @Test
    fun `a sheet's result off Paying changes nothing, a second tap or a recreated screen`() {
        val back = reduce(paying(), BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard)).session

        listOf(back, initial(), quoted()).forEach { session ->
            val step = reduce(session, BuyCryptoAction.PaymentEnded(MoonPayPaymentRetry.AnotherCard))
            assertEquals(session, step.session)
            assertTrue(step.events.isEmpty())
        }
    }

    @Test
    fun `a range landing while continuing does not change the amount`() {
        val loaded = loaded()
        val continuing = reduce(reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer(), failed = false, offline = false, atMs = 0L)).session, BuyCryptoAction.Continue).session
        val emptied = continuing.copy(amount = AmountState(text = "50", suggested = "50", revision = 0))

        val step = reducer.reduce(emptied, Command.RangeLoaded(setup, RampAmountRange(suggested = BigDecimal("30"), max = null)))

        assertEquals("50", step.session.amount.text)
        assertEquals(emptied.quoteKey, step.session.quoteKey)
    }

    @Test
    fun `the timer and the rate's retry ask for nothing while MoonPay pays`() {
        val paying = paying()

        assertEquals(paying, reduce(paying, BuyCryptoAction.Requote).session)
    }

    @Test
    fun `a load or a quote for something no longer asked for changes nothing`() {
        val loaded = loaded()
        val stale = reduce(loaded, BuyCryptoAction.Reload).session

        assertEquals(stale, reducer.reduce(stale, Command.SetupLoaded(loaded.setupKey, setup)).session)
        val requested = reduce(quoted(), BuyCryptoAction.SetAmount("60")).session
        assertEquals(requested, reducer.reduce(requested, Command.Quoted(quoted().quoteKey!!, offer(), failed = false, offline = false, atMs = 0L)).session)
    }

    @Test
    fun `an amount that comes back asks for its quote again with a new key`() {
        val loaded = loaded()
        val first = loaded.quoteKey!!

        val back = listOf("", "50").fold(loaded) { session, text -> reduce(session, BuyCryptoAction.SetAmount(text)).session }

        assertTrue(back.quote.loading)
        assertFalse(back.quoteKey == first)
        assertEquals(first.amount, back.quoteKey!!.amount)
    }

    @Test
    fun `every load gets a new load number in its key`() {
        val loaded = loaded()

        val reloaded = reduce(loaded, BuyCryptoAction.Reload).session

        assertFalse(reloaded.setupKey == loaded.setupKey)
        assertEquals(loaded.setupKey.load + 1, reloaded.setupKey.load)
    }

    @Test
    fun `a new card continues without a quote, and not with an amount out of limits`() {
        val loaded = loaded(newCard)
        val outOfLimits = reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, RampOffer.OutOfLimits(null, RampLimitViolation.BelowMin(BigDecimal("20"))), failed = false, offline = false, atMs = 0L)).session

        val continuing = reduce(loaded, BuyCryptoAction.Continue).session

        assertEquals(null, (continuing.phase as Phase.Continuing).offer)
        assertEquals(outOfLimits, reduce(outOfLimits, BuyCryptoAction.Continue).session)
    }

    @Test
    fun `an added card is what the screen loads and pays with next`() {
        val continuing = reduce(loaded(newCard), BuyCryptoAction.Continue).session

        val step = reducer.reduce(continuing, Command.ContinueEnded(1, RampCheckout.CardAdded("new")))

        assertEquals(Phase.Idle, step.session.phase)
        assertTrue(step.session.loading)
        assertEquals("card:new", step.session.request.method)
    }

    @Test
    fun `a removed card the screen pays with loads the screen again, any other is only remembered`() {
        val loaded = loaded()

        assertTrue(reduce(loaded, BuyCryptoAction.CardsRemoved(setOf("visa"))).session.loading)
        val loading = reduce(loaded.copy(request = loaded.request.copy(method = "card:visa")), BuyCryptoAction.Reload).session
        val reloaded = reduce(loading, BuyCryptoAction.CardsRemoved(setOf("visa"))).session
        assertTrue(reloaded.reload > loading.reload)
        assertEquals(null, reloaded.request.method)
        assertEquals(reloaded.copy(removedCards = setOf("visa", "other")), reduce(reloaded, BuyCryptoAction.CardsRemoved(setOf("visa", "other"))).session)
        assertEquals(loaded.copy(removedCards = setOf("other")), reduce(loaded, BuyCryptoAction.CardsRemoved(setOf("other"))).session)
    }

    @Test
    fun `Google Pay on MoonPay is paid from MoonPay's button, never from Continue, unless its quote needs a challenge`() {
        val loaded = loaded(googlePay)
        val executable = reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer(gpQuote("q1")), failed = false, offline = false, atMs = 0L)).session
        val challenged = reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer(gpQuote("q1").copy(executable = false)), failed = false, offline = false, atMs = 0L)).session

        assertEquals(ButtonPhase.Loading, executable.button?.phase)
        assertEquals("q1", executable.button?.loaded?.signature)
        assertFalse(executable.canContinue)
        assertSame(executable, reduce(executable, BuyCryptoAction.Continue).session)
        assertEquals(null, challenged.button)
        assertTrue(challenged.canContinue)
    }

    @Test
    fun `one button per amount, a newer quote of it goes into the frame, and it stays while that quote loads`() {
        val first = googlePayQuoted("q1")
        val refreshing = reduce(first, BuyCryptoAction.Requote).session
        val refreshed = reducer.reduce(refreshing, Command.Quoted(refreshing.quoteKey!!, offer(gpQuote("q2")), failed = false, offline = false, atMs = 0L)).session
        val typing = reduce(refreshed, BuyCryptoAction.SetAmount("60")).session
        val other = reducer.reduce(typing, Command.Quoted(typing.quoteKey!!, offer(gpQuote("q3"), "60"), failed = false, offline = false, atMs = 0L)).session

        assertEquals(first.button, refreshing.button)
        assertEquals(first.button?.id, refreshed.button?.id)
        assertEquals("q2", refreshed.button?.inFrame?.signature)
        assertEquals("q1", refreshed.button?.loaded?.signature)
        assertEquals(refreshed.button, typing.button)
        assertTrue(other.button?.id != first.button?.id)
        assertEquals("q3", other.button?.loaded?.signature)
    }

    @Test
    fun `a covered screen, another method or a reload drop the button`() {
        val quoted = googlePayQuoted("q1")

        assertEquals(null, reduce(quoted, BuyCryptoAction.SetVisible(false)).session.button)
        assertEquals(null, reduce(quoted, BuyCryptoAction.Reload).session.button)
    }

    @Test
    fun `a tap opens the sheet with the frame's quote, spends it, and the closed sheet asks for a newer one`() {
        val quoted = googlePayQuoted("q1")
        val id = quoted.button!!.id
        val data = payment.copy(quote = gpQuote("q1"), externalTransactionId = id)
        val pressed = reducer.reduce(quoted, Command.ButtonPressed(id, data))
        val ended = reduce(pressed.session, BuyCryptoAction.PaymentEnded(retry = null)).session
        val sameAgain = reducer.reduce(ended, Command.Quoted(ended.quoteKey!!, offer(gpQuote("q1")), failed = false, offline = false, atMs = 0L)).session
        val newer = reducer.reduce(ended, Command.Quoted(ended.quoteKey!!, offer(gpQuote("q2")), failed = false, offline = false, atMs = 0L)).session

        assertEquals(Phase.Paying, pressed.session.phase)
        assertEquals(ButtonPhase.Pressed, pressed.session.button?.phase)
        assertEquals(setOf("q1"), pressed.session.spent)
        assertEquals("q1", (pressed.events.single() as BuyCryptoEvent.OpenMoonPay).data.quote.signature)
        assertEquals(listOf("Continued", "ToProvider"), pressed.tracks.map { it::class.simpleName })
        assertTrue(reducer.reduce(pressed.session, Command.ButtonPressed(id, data)).events.isEmpty())
        assertEquals(null, ended.button)
        assertEquals(quoted.quoteAttempt + 1, ended.quoteAttempt)
        assertEquals(null, sameAgain.button)
        assertEquals("q2", newer.button?.loaded?.signature)
    }

    @Test
    fun `a failed button is told once per setup load and amount, never for an expired quote or a covered screen, and comes back only with a newer quote`() {
        val quoted = googlePayQuoted("q1")
        val id = quoted.button!!.id
        val first = reducer.reduce(quoted, Command.ButtonEnded(id, quiet = false, signInRequired = false))
        val requoting = reduce(first.session, BuyCryptoAction.Requote).session
        val sameQuote = reducer.reduce(requoting, Command.Quoted(requoting.quoteKey!!, offer(gpQuote("q1")), failed = false, offline = false, atMs = 0L)).session
        val newer = reducer.reduce(requoting, Command.Quoted(requoting.quoteKey!!, offer(gpQuote("q2")), failed = false, offline = false, atMs = 0L)).session
        val again = reducer.reduce(newer, Command.ButtonEnded(newer.button!!.id, quiet = false, signInRequired = false))

        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), first.events)
        assertEquals(ButtonPhase.Ended, first.session.button?.phase)
        assertFalse(first.session.canContinue)
        assertEquals(ButtonPhase.Ended, sameQuote.button?.phase)
        assertEquals(ButtonPhase.Loading, newer.button?.phase)
        assertTrue(again.events.isEmpty())
        assertTrue(reducer.reduce(quoted, Command.ButtonEnded(id, quiet = true, signInRequired = false)).events.isEmpty())
        assertTrue(reducer.reduce(quoted, Command.ButtonEnded("other", quiet = false, signInRequired = false)).events.isEmpty())
    }

    @Test
    fun `a button that lost MoonPay's sign-in checks MoonPay again without a toast, once per load`() {
        val quoted = googlePayQuoted("q1")
        val id = quoted.button!!.id

        val ended = reducer.reduce(quoted, Command.ButtonEnded(id, quiet = false, signInRequired = true))
        val again = reducer.reduce(
            quoted.copy(buttonSignIn = quoted.loads),
            Command.ButtonEnded(id, quiet = false, signInRequired = true),
        )

        assertTrue(ended.events.isEmpty())
        assertTrue(ended.session.loading)
        assertTrue(ended.session.recheck)
        assertEquals(quoted.loads + 1, ended.session.loads)
        assertEquals(ended.session.loads, ended.session.buttonSignIn)
        assertEquals(listOf<BuyCryptoEvent>(BuyCryptoEvent.Failed), again.events)
        assertEquals(quoted.loads, again.session.loads)
        assertTrue(reducer.reduce(quoted, Command.ButtonEnded("other", quiet = false, signInRequired = true)).events.isEmpty())
    }

    @Test
    fun `a zero after a lone zero starts the decimals, up to the fiat's precision`() {
        var session = typed(loaded(), "0")
        assertEquals("0", session.amount.text)
        session = typed(session, "00")
        assertEquals("0.0", session.amount.text)
        session = typed(session, "0.00")
        assertEquals("0.00", session.amount.text)
        session = typed(session, "0.000")
        assertEquals("0.00", session.amount.text)
    }

    @Test
    fun `other leading zeros go`() {
        assertEquals("5", typed(typed(loaded(), "0"), "05").amount.text)
        assertEquals("0", typed(typed(loaded(), "100"), "00").amount.text)
    }

    @Test
    fun `the amount stops at a billion`() {
        val billion = typed(loaded(), "1000000000")
        assertEquals("1000000000", billion.amount.text)
        assertEquals("1000000000", typed(billion, "10000000000").amount.text)
        assertEquals("1000000000", typed(billion, "1000000001").amount.text)
    }

    @Test
    fun `MoonPay's disclosures stay while the next amount is quoted, and go with a reload or a merchant's quote`() {
        val disclosures = listOf(MoonPayBuyQuote.PaymentDisclosure("eea-crypto-asset-risk", "1"))
        val loaded = loaded()
        val quoted = reducer.reduce(
            loaded,
            Command.Quoted(loaded.quoteKey!!, offer(payment.quote.copy(paymentDisclosures = disclosures)), failed = false, offline = false, atMs = 0L),
        ).session

        assertTrue(loaded.paymentDisclosures.isEmpty())
        assertEquals(disclosures, quoted.paymentDisclosures)
        assertEquals(disclosures, typed(quoted, "60").paymentDisclosures)
        assertTrue(reduce(quoted, BuyCryptoAction.Reload).session.paymentDisclosures.isEmpty())
        val merchant = offer().copy(ticket = RampTicket.Merchant(ExchangeMerchantSlug.mercuryo, "order", "Mercuryo"))
        val requoted = typed(quoted, "60")
        assertTrue(reducer.reduce(requoted, Command.Quoted(requoted.quoteKey!!, merchant, failed = false, offline = false, atMs = 0L)).session.paymentDisclosures.isEmpty())
    }

    @Test
    fun `the same amount put in again, as the field may on losing focus, asks for no quote`() {
        val quoted = quoted()

        val again = typed(quoted, "50")

        assertSame(quoted.quoteKey, again.quoteKey)
        assertEquals(quoted.quote, again.quote)
        val loading = typed(quoted, "60")
        assertSame(loading.quoteKey, typed(loading, "60").quoteKey)
    }

    @Test
    fun `the same amount put in again after a failed quote asks again`() {
        val loaded = loaded()
        val failed = reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer = null, failed = true, offline = false, atMs = 0L)).session

        val again = typed(failed, "50")

        assertTrue(again.quote.loading)
        assertFalse(again.quoteKey == failed.quoteKey)
        assertEquals(failed.quoteKey!!.amount, again.quoteKey!!.amount)
        // A quote that went through ends the retry: the same amount again asks for nothing.
        val retried = reducer.reduce(again, Command.Quoted(again.quoteKey!!, offer(), failed = false, offline = false, atMs = 0L)).session
        assertSame(retried.quoteKey, typed(retried, "50").quoteKey)
    }

    @Test
    fun `a refresh that went through after a failed one ends the retry`() {
        val quoted = quoted()
        val refreshing = reduce(quoted, BuyCryptoAction.Requote).session
        val failed = reducer.reduce(refreshing, Command.Quoted(refreshing.quoteKey!!, offer = null, failed = true, offline = false, atMs = 0L)).session
        val timer = reduce(failed, BuyCryptoAction.Requote).session
        val refreshed = reducer.reduce(timer, Command.Quoted(timer.quoteKey!!, offer(), failed = false, offline = false, atMs = 0L)).session

        assertSame(refreshed.quoteKey, typed(refreshed, "50").quoteKey)
    }

    @Test
    fun `the same amount after an answer out of limits asks for nothing`() {
        val loaded = loaded()
        val outOfLimits = RampOffer.OutOfLimits(badgeUrl = null, violation = RampLimitViolation.BelowMin(BigDecimal("60")))
        val limited = reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, outOfLimits, failed = false, offline = false, atMs = 0L)).session

        assertSame(limited.quoteKey, typed(limited, "50").quoteKey)
    }

    @Test
    fun `a saved card picked after another method drops that method's disclosures`() {
        val disclosures = listOf(MoonPayBuyQuote.PaymentDisclosure("us-transaction-finality", "1"))
        val loaded = loaded(googlePay)
        val quoted = reducer.reduce(
            loaded,
            Command.Quoted(loaded.quoteKey!!, offer(gpQuote("gp").copy(paymentDisclosures = disclosures)), failed = false, offline = false, atMs = 0L),
        ).session
        assertEquals(disclosures, quoted.paymentDisclosures)

        val picked = reduce(
            quoted,
            BuyCryptoAction.SelectMethod(method.id, "USD", method.title, method.subtitle, null, card = null, moonPay = true),
        ).session
        val card = reducer.reduce(picked, Command.SetupLoaded(picked.setupKey, setup)).session

        assertTrue(picked.paymentDisclosures.isEmpty())
        assertTrue(card.paymentDisclosures.isEmpty())
    }

    @Test
    fun `MoonPay's disclosures stay through a failed quote of another amount`() {
        val disclosures = listOf(MoonPayBuyQuote.PaymentDisclosure("us-transaction-finality", "1"))
        val loaded = loaded()
        val quoted = reducer.reduce(
            loaded,
            Command.Quoted(loaded.quoteKey!!, offer(payment.quote.copy(paymentDisclosures = disclosures)), failed = false, offline = false, atMs = 0L),
        ).session
        val typing = typed(quoted, "60")

        val failed = reducer.reduce(typing, Command.Quoted(typing.quoteKey!!, offer = null, failed = true, offline = false, atMs = 0L)).session

        assertEquals(disclosures, failed.paymentDisclosures)
        assertEquals(disclosures, typed(quoted, "").paymentDisclosures)
    }

    private fun typed(session: Session, text: String): Session {
        return reduce(session, BuyCryptoAction.SetAmount(text)).session
    }

    private fun initial(): Session {
        return Session(
            request = SetupRequest(fiat = null, assetId = null, method = null),
            amount = AmountState(text = "50", suggested = null, revision = 0),
            moonPay = MoonPayAccess.Connected,
        )
    }

    private fun loaded(with: RampPaymentMethod = method): Session {
        val initial = initial()
        return reducer.reduce(initial, Command.SetupLoaded(initial.setupKey, setup.takeIf { with == method } ?: Setup(asset, "USD", with, moonPay))).session
    }

    private fun quoted(): Session {
        val loaded = loaded()
        return reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer(), failed = false, offline = false, atMs = 0L)).session
    }

    private fun continuing(): Session {
        return reduce(quoted(), BuyCryptoAction.Continue).session
    }

    private fun paying(): Session {
        return reducer.reduce(continuing(), Command.ContinueEnded(1, RampCheckout.Pay(payment))).session
    }

    private fun reduce(session: Session, action: BuyCryptoAction): Step {
        return reducer.reduce(session, Command.Act(action))
    }

    private fun gpQuote(signature: String): MoonPayBuyQuote {
        return payment.quote.copy(signature = signature, executable = true, paymentMethod = MoonPayPaymentMethodReference(type = "google_pay"))
    }

    private fun googlePayQuoted(signature: String): Session {
        val loaded = loaded(googlePay)
        return reducer.reduce(loaded, Command.Quoted(loaded.quoteKey!!, offer(gpQuote(signature)), failed = false, offline = false, atMs = 0L)).session
    }

    private fun offer(quote: MoonPayBuyQuote = payment.quote, amount: String = "50"): RampOffer.Available {
        return RampOffer.Available(
            request = RampQuoteRequest("usdt", "USD", method, BigDecimal(amount)),
            receive = BigDecimal("49"),
            rate = BigDecimal("0.98"),
            lifetime = null,
            badgeUrl = null,
            ticket = RampTicket.MoonPay(quote),
        )
    }
}
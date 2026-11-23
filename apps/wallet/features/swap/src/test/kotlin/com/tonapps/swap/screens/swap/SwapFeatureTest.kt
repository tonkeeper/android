package com.tonapps.swap.screens.swap

import android.os.SystemClock
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.swap.SwapRoutes
import com.tonapps.swap.screens.swap.SwapFixtures.BTC_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ADDRESS
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.WALLET_ID
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.CrossSwapExactType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What each action the screen can send does to the nodes it reads.
 *
 * Every step is "the user did this, the screen then showed that" — see [SwapScreen] for the four
 * ways to move time. Time is virtual, so a wait is an assertion ("nothing was requested yet")
 * rather than a pause, and [SwapTestEnv] says what is real and what is recorded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    @Test
    fun loadsTheDefaultPairFromConfig() = swapTest(env) {
        val opened = now()

        assertEquals(TON_ASSET_ID, opened.sellAsset?.id)
        assertEquals(USDT_TON_ASSET_ID, opened.buyAsset?.id)
        assertEquals(TON_ADDRESS, opened.sellAccount?.address?.display)
        // eth's config entry (200 bps default) never applies: the sell side is on TON.
        assertEquals(100, opened.selectedSlippageBps)
    }

    @Test
    fun sellAmountAsksForAnExactInputQuote() = swapTest(env) {
        // The debounce is a real part of the contract: nothing is spent while the user types.
        send { sendAction(SwapAction.Amount.SetSell("1")) }
        pass(timings.quoteDebounce)
        assertTrue(quoteRequests.isEmpty())

        val quoted = after()

        val request = quoteRequests.single()
        assertEquals(CrossSwapExactType.exact_input, request.exactType)
        assertEquals("1000000000", request.sourceAmount)
        assertNull(request.destinationAmount)
        assertEquals(TON_ASSET_ID, request.sourceAsset)
        assertEquals(USDT_TON_ASSET_ID, request.destinationAsset)
        assertEquals(TON_ADDRESS, request.senderAddress)
        assertEquals(TON_ADDRESS, request.recipientAddress)
        assertEquals(100, request.slippageBps)

        val quote = quoted.quote!!
        assertEquals("1.417408", quote.buyAmount)
        assertEquals("1", quote.sellAmount)
        assertEquals(BigInteger.parseString("1417408"), quote.quote.buyBaseAmount)
        // Reads wrong on purpose: the label is built from the chainkit asset, whose symbol for TON
        // is "GRAM" (CoinType.Gram.symbol; its shortName is "TON").
        assertEquals("1 GRAM ≈ 1.417408 USDT", quote.rateLabel)
        assertTrue(quoted.continueEnabled)
    }

    @Test
    fun rateLabelKeepsItsLeftToRightOrderInRtl() {
        val defaults = Formatter.defaultOptions
        Formatter.configureDefaults(defaults.copy(isolateLtr = true))
        try {
            swapTest(env) {
                val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }

                assertEquals("\u20661 GRAM \u2066≈ 1.417408 USDT\u2069\u2069", quoted.quote?.rateLabel)
            }
        } finally {
            Formatter.configureDefaults(defaults)
        }
    }

    @Test
    fun amountsWrittenIntoTheFieldsFollowTheLocaleSeparator() {
        val defaults = Formatter.defaultOptions
        Formatter.configureDefaults(defaults.copy(decimalSeparator = ","))
        try {
            swapTest(env) {
                val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
                assertEquals("1,417408", quoted.quote?.buyAmount)

                val inFiat = after { sendAction(SwapAction.Amount.ToggleCurrency) }
                assertEquals("1,43", inFiat.amountInput.text)

                after { sendAction(SwapAction.Amount.SetSell("2,86")) }
                assertEquals("2000000000", quoteRequests.last().sourceAmount)
            }
        } finally {
            Formatter.configureDefaults(defaults)
        }
    }

    /** The spinner has to cover the debounce, or the receive field reads as empty and final. */
    @Test
    fun theSpinnerIsUpBeforeTheRequestGoesOut() = swapTest(env) {
        val typed = send { sendAction(SwapAction.Amount.SetSell("1")) }

        assertTrue(typed.isLoading)
        assertNull(typed.quote)
        assertTrue(quoteRequests.isEmpty())
    }

    @Test
    fun receiveAmountAsksForAnExactOutputQuote() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetBuy("20")) }

        val request = quoteRequests.single()
        assertEquals(CrossSwapExactType.exact_output, request.exactType)
        assertEquals("20000000", request.destinationAmount)
        assertNull(request.sourceAmount)

        // exact_output prices the side the user did not fix, so the send field follows the quote.
        assertEquals("14.35032486", quoted.quote?.sellAmount)
        assertEquals(EXACT_OUTPUT_SOURCE, quoted.quote?.quote?.sourceBaseAmount)
        assertEquals(EXACT_OUTPUT_SOURCE, quoted.sellAmount)

        // 14.35 TON against a 5 TON balance: the price of the receive side is what the funds are
        // measured against, so this pair of nodes is the only thing standing between the user and
        // a swap the wallet cannot pay for.
        assertTrue(quoted.insufficientFunds)
        assertFalse(quoted.continueEnabled)
    }

    @Test
    fun receiveAmountSendsTheQuotedSourceAmountToConfirm() {
        env.accounts[TON_ASSET_ID] = testAccount(SwapFixtures.tonAsset, balance = "20000000000", price = "1.43")

        swapTest(env) {
            val quoted = after { sendAction(SwapAction.Amount.SetBuy("20")) }
            assertFalse(quoted.insufficientFunds)
            assertTrue(quoted.continueEnabled)

            after { sendAction(SwapAction.Continue) }

            // Nothing on this path was typed by the user: the amount to send is the quote's.
            val swap = (events.single() as SwapEvent.Continue).request.type as ConfirmType.Swap
            assertEquals(EXACT_OUTPUT_SOURCE, swap.sourceAmount)
            assertEquals(USDT_TON_ASSET_ID, swap.destinationAssetId)
        }
    }

    /** The hard switch pins exact-input requests to SwapKit; SwapKit has no exact-output quote. */
    @Test
    fun receiveAmountNeverPinsTheAggregator() =
        swapTest(SwapTestEnv(context, timeSource, features, swapKitEnabled = true)) {
            after { sendAction(SwapAction.Amount.SetBuy("20")) }

            val request = quoteRequests.single()
            assertEquals(CrossSwapExactType.exact_output, request.exactType)
            assertNull(request.aggregators)
        }

    @Test
    fun receiveAmountIsIgnoredWhenExactOutputIsOff() =
        swapTest(SwapTestEnv(context, timeSource, features, exactOutputEnabled = false)) {
            assertFalse(feature.isReceiveAmountEditable)

            val ignored = after { sendAction(SwapAction.Amount.SetBuy("20")) }

            assertEquals(AmountInput(), ignored.amountInput)
            assertTrue(quoteRequests.isEmpty())
        }

    @Test
    fun swapSidesMovesTheAmountWithItsToken() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val flipped = after { sendAction(SwapAction.Asset.Swap) }

        assertEquals(USDT_TON_ASSET_ID, flipped.sellAsset?.id)
        assertEquals(TON_ASSET_ID, flipped.buyAsset?.id)

        // "1" was 1 TON before the flip and stays 1 TON after it, which is now the receive side.
        assertEquals("1", flipped.amountInput.text)
        assertEquals(AmountInputSide.Buy, flipped.amountInput.side)

        val request = quoteRequests.last()
        assertEquals(CrossSwapExactType.exact_output, request.exactType)
        assertEquals(USDT_TON_ASSET_ID, request.sourceAsset)
        assertEquals(TON_ASSET_ID, request.destinationAsset)
        assertEquals("1000000000", request.destinationAmount)
    }

    /** Without the exact-output flag the receive side cannot be priced, so a flip keeps the
     *  amount on the send side — it now means the token that just moved there. */
    @Test
    fun swapSidesKeepsTheAmountOnTheSendSideWhenExactOutputIsOff() =
        swapTest(SwapTestEnv(context, timeSource, features, exactOutputEnabled = false)) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }
            val flipped = after { sendAction(SwapAction.Asset.Swap) }

            assertEquals("1", flipped.amountInput.text)
            assertEquals(AmountInputSide.Sell, flipped.amountInput.side)

            val request = quoteRequests.last()
            assertEquals(CrossSwapExactType.exact_input, request.exactType)
            assertEquals(USDT_TON_ASSET_ID, request.sourceAsset)
            assertEquals("1000000", request.sourceAmount)
        }

    @Test
    fun pickingTheAssetOfTheOtherSideFlipsThePair() = swapTest(env) {
        val flipped = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.usdtTonAsset)) }

        assertEquals(USDT_TON_ASSET_ID, flipped.sellAsset?.id)
        assertEquals(TON_ASSET_ID, flipped.buyAsset?.id)
    }

    @Test
    fun maxKeepsTheChainReserveAside() = swapTest(env) {
        val maxed = after { sendAction(SwapAction.Amount.Max) }

        assertTrue(maxed.amountInput.isMax)
        // 5 TON balance minus the 1 TON reserve the swap leaves for network fees.
        assertEquals("4000000000", quoteRequests.single().sourceAmount)
    }

    @Test
    fun amountAboveTheBalanceBlocksContinue() = swapTest(env) {
        val overBalance = after { sendAction(SwapAction.Amount.SetSell("50")) }

        assertTrue(overBalance.insufficientFunds)
        // Even with a quote in hand the balance keeps the button off.
        assertTrue(overBalance.hasQuote)
        assertFalse(overBalance.continueEnabled)
    }

    @Test
    fun toggleCurrencySwitchesTheFieldToFiat() = swapTest(env) {
        val inFiat = after {
            sendAction(SwapAction.Amount.SetSell("1"))
            sendAction(SwapAction.Amount.ToggleCurrency)
        }

        // 1 TON at the account's 1.43 USD rate.
        assertTrue(inFiat.amountInput.inFiat)
        assertEquals("1.43", inFiat.amountInput.text)
    }

    @Test
    fun togglingBackRestoresTheTypedAmount() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("0.123456789")) }

        val inFiat = after { sendAction(SwapAction.Amount.ToggleCurrency) }
        assertEquals("0.17", inFiat.amountInput.text)

        val back = after { sendAction(SwapAction.Amount.ToggleCurrency) }
        assertFalse(back.amountInput.inFiat)
        assertEquals("0.123456789", back.amountInput.text)
        assertEquals("123456789", quoteRequests.last().sourceAmount)

        val inFiatAgain = after { sendAction(SwapAction.Amount.ToggleCurrency) }
        assertEquals("0.17", inFiatAgain.amountInput.text)
    }

    @Test
    fun maxUnitsSurviveAToggleThereAndBack() = swapTest(env) {
        val maxed = after { sendAction(SwapAction.Amount.Max) }

        after { sendAction(SwapAction.Amount.ToggleCurrency) }
        val back = after { sendAction(SwapAction.Amount.ToggleCurrency) }

        assertTrue(back.amountInput.isMax)
        assertEquals(maxed.amountInput.maxUnits, back.amountInput.maxUnits)
    }

    @Test
    fun anAmountEditedAfterTheToggleIsConvertedAgain() = swapTest(env) {
        after {
            sendAction(SwapAction.Amount.SetSell("1"))
            sendAction(SwapAction.Amount.ToggleCurrency)
            sendAction(SwapAction.Amount.SetSell("2.86"))
        }

        val back = after { sendAction(SwapAction.Amount.ToggleCurrency) }

        assertFalse(back.amountInput.inFiat)
        assertEquals("2", back.amountInput.text)
    }

    /**
     * The field can be entered in dollars, the chain cannot: what the user sees as a price has to
     * reach the aggregator as tokens, or the swap is for the wrong amount.
     */
    @Test
    fun fiatAmountIsQuotedInTokens() = swapTest(env) {
        val quoted = after {
            sendAction(SwapAction.Amount.ToggleCurrency)
            sendAction(SwapAction.Amount.SetSell("2.86"))
        }

        assertTrue(quoted.amountInput.inFiat)
        // 2.86 USD at the account's 1.43 USD rate.
        assertEquals("2000000000", quoteRequests.single().sourceAmount)
        assertEquals("2", quoted.quote?.sellAmount)
        assertFalse(quoted.insufficientFunds)
    }

    @Test
    fun continueCarriesTheQuoteToTheConfirmRequest() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertTrue(quoted.continueEnabled)

        after { sendAction(SwapAction.Continue) }

        val event = events.single()
        assertTrue(event is SwapEvent.Continue)
        val request = (event as SwapEvent.Continue).request
        assertEquals(TON_ASSET_ID, request.data.assetId)
        assertEquals(WALLET_ID, request.data.walletId)

        val swap = request.type
        assertTrue(swap is ConfirmType.Swap)
        swap as ConfirmType.Swap
        assertEquals(USDT_TON_ASSET_ID, swap.destinationAssetId)
        assertEquals(BigInteger.parseString("1000000000"), swap.sourceAmount)
        assertEquals(false, swap.isMax)
        assertEquals(quoted.quote?.quote?.routeId, swap.quote?.routeId)
        assertEquals(quoted.quote?.quote?.buyBaseAmount, swap.quote?.buyAmount)
        assertFalse("the classic flow hands the swap to the confirm screen", swap.oneClick)
    }

    /** Behind its flag a multichain wallet quotes on v3, and confirm is told to prepare the route there too. */
    @Test
    fun continueCarriesAV3QuoteToTheConfirmRequest() =
        swapTest(SwapTestEnv(context, timeSource, features, swapV3Enabled = true)) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }
            after { sendAction(SwapAction.Continue) }

            assertTrue(quoteRequests.isEmpty())
            assertEquals("1000000000", env.quoteRequestsV3.single().sourceAmount)
            val swap = (events.single() as SwapEvent.Continue).request.type as ConfirmType.Swap
            val quote = checkNotNull(swap.quote)
            assertEquals(SwapFixtures.v3Quote.routes.single().routeId, quote.routeId)
            assertTrue(quote.genericPayload)
        }

    @Test
    fun slippageChoiceGoesIntoTheNextQuote() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertEquals(100, quoteRequests.single().slippageBps)

        val reslipped = after { sendAction(SwapAction.SelectSlippage(300)) }

        // The amount did not change, but the price the user accepts did: that is a new quote.
        assertEquals(2, quoteRequests.size)
        assertEquals(300, quoteRequests.last().slippageBps)
        assertEquals(300, reslipped.selectedSlippageBps)
    }

    /** Slippage is a per-chain setting, so a chain switch drops the choice made for the old one. */
    @Test
    fun slippageChoiceIsDroppedWhenTheSellChainChanges() = swapTest(env) {
        after {
            sendAction(SwapAction.SelectSlippage(10))
            sendAction(SwapAction.Amount.SetSell("0.001"))
        }
        assertEquals(10, quoteRequests.single().slippageBps)

        val onBitcoin = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.btcAsset)) }

        // btc's config entry, not the 10 bps the user picked while selling TON.
        assertEquals(300, onBitcoin.selectedSlippageBps)
        assertEquals(300, quoteRequests.last().slippageBps)
    }

    @Test
    fun aCompletedSwapClearsTheAmount() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertTrue(quoted.hasQuote)

        val reset = after { env.swapRepo.notifySwapCompleted(tracked = false) }

        assertEquals("", reset.amountInput.text)
        assertEquals(AmountInputSide.Sell, reset.amountInput.side)
        assertNull(reset.quote)
        assertFalse(reset.continueEnabled)
    }

    /** An amount that is not a positive number is not an amount: nothing is spent on it. */
    @Test
    fun amountsThatAreNotPositiveNumbersAreNotQuoted() = swapTest(env) {
        listOf("0", "0.00", ".", "abc").forEach { text ->
            val typed = after { sendAction(SwapAction.Amount.SetSell(text)) }

            assertTrue("quoted \"$text\"", quoteRequests.isEmpty())
            assertNull(typed.quote)
            assertFalse(typed.isLoading)
            assertFalse(typed.continueEnabled)
        }
    }

    /** Opening the screen on a token asks the config for that pair, not for the default one. */
    @Test
    fun theRoutesPairIsWhatTheConfigIsAskedFor() = swapTest(
        env = env,
        route = SwapRoutes.Swap(sellAssetId = ETH_ASSET_ID, buyAssetId = BTC_ASSET_ID),
    ) {
        assertEquals(Triple(WALLET_ID, ETH_ASSET_ID, BTC_ASSET_ID), env.configRequests.single())
    }

    @Test
    fun theRoutesInitiatorGoesToConfirm() {
        env.accounts[TON_ASSET_ID] = testAccount(SwapFixtures.tonAsset, balance = "20000000000", price = "1.43")

        swapTest(env = env, route = SwapRoutes.Swap(initiator = ConfirmInitiator.DeepLink)) {
            after { sendAction(SwapAction.Amount.SetBuy("20")) }
            after { sendAction(SwapAction.Continue) }

            assertEquals(ConfirmInitiator.DeepLink, (events.single() as SwapEvent.Continue).request.initiator)
        }
    }

    @Test
    fun theRoutesUtmGoesToConfirm() {
        env.accounts[TON_ASSET_ID] = testAccount(SwapFixtures.tonAsset, balance = "20000000000", price = "1.43")
        val utm = UtmParameters(source = "newsletter", campaign = "autumn")

        swapTest(env = env, route = SwapRoutes.Swap(initiator = ConfirmInitiator.DeepLink, utm = utm)) {
            after { sendAction(SwapAction.Amount.SetBuy("20")) }
            after { sendAction(SwapAction.Continue) }

            assertEquals(utm, (events.single() as SwapEvent.Continue).request.utm)
        }
    }

    /** The countdown under the rate, and the requote it closes — 30s of it, instantly. */
    @Test
    fun theQuoteTtlRequotesWhenTheTimerRunsOut() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        // Not exactly 1: proving the screen went quiet costs the countdown a tick.
        assertEquals(1f, quoted.quoteTimer!!, 0.05f)

        val halfway = pass(timings.quoteTtl / 2)
        assertEquals(0.5f, halfway.quoteTimer!!, 0.02f)
        assertEquals(1, quoteRequests.size)

        pass(timings.quoteTtl / 2)
        val requoted = after()

        assertEquals(2, quoteRequests.size)
        // Back to a full bar: waiting for the new quote costs the countdown a tick or two.
        assertEquals(1f, requoted.quoteTimer!!, 0.05f)
    }

    @Test
    fun anExpiredQuoteIsRequotedOnlyOnceTheAppIsBack() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        env.appInForeground.value = false

        pass(timings.quoteTtl * 2)
        assertEquals(1, quoteRequests.size)

        after { env.appInForeground.value = true }

        assertEquals(2, quoteRequests.size)
    }

    @Test
    fun aQuoteStillGoodOnReturnKeepsItsCountdown() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        env.appInForeground.value = false
        pass(timings.quoteTtl / 3)

        send { env.appInForeground.value = true }
        pass(timings.quoteTtl / 3)

        assertEquals(1, quoteRequests.size)
    }

    /** The screen closes on a completed swap; it hears about it through the event, not the amount. */
    @Test
    fun aCompletedSwapIsAnEvent() = swapTest(env) {
        after { env.swapRepo.notifySwapCompleted(tracked = false) }

        assertEquals(listOf<SwapEvent>(SwapEvent.Completed(tracked = false)), events)
    }

    /** The confirm screen renders the quote it is handed: every field of it has to arrive. */
    @Test
    fun confirmRequestCarriesTheWholeQuote() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        val route = checkNotNull(quoted.quote).quote

        after { sendAction(SwapAction.Continue) }

        val swap = (events.single() as SwapEvent.Continue).request.type as ConfirmType.Swap
        val handed = checkNotNull(swap.quote)
        assertEquals(route.routeId, handed.routeId)
        assertEquals(route.buyBaseAmount, handed.buyAmount)
        assertEquals(route.minimumBuyBaseAmount, handed.minBuyAmount)
        assertEquals(route.providerTxId, handed.providerTxId)
        assertEquals(route.slippageBps, handed.slippageBps)
        assertEquals(route.priceImpactBps, handed.priceImpactBps)
        assertEquals(route.provider.id, handed.provider)
        assertEquals(quoted.selectedSlippageBps, swap.slippageBps)
    }

    /** Slippage is per chain, so assets on the chain the user picked it for keep the choice. */
    @Test
    fun slippageChoiceSurvivesAssetsOnTheSameChain() = swapTest(env) {
        after { sendAction(SwapAction.SelectSlippage(10)) }

        val newBuy = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        assertEquals(10, newBuy.selectedSlippageBps)

        // USDT on TON: another asset, the same chain.
        val newSell = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.usdtTonAsset)) }
        assertEquals(10, newSell.selectedSlippageBps)
    }

    /** Hidden balances mask both sides' balance lines; MAX still works from the real balance. */
    @Test
    fun hiddenBalancesAreMasked() {
        env.hiddenBalances = true

        swapTest(env) {
            assertEquals(HIDDEN_BALANCE, feature.sellBalance.value)
            assertEquals(HIDDEN_BALANCE, feature.buyBalance.value)

            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertTrue(maxed.amountInput.isMax)
            assertEquals("4000000000", quoteRequests.single().sourceAmount)
        }
    }

    @Test
    fun balancesAreShownShort() = swapTest(env) {
        assertEquals("5", feature.sellBalance.value)
        assertEquals("0", feature.buyBalance.value)
    }

    @Test
    fun aQuoteStartsABackgroundAttempt() = redesignTest {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }

        val attempt = checkNotNull(feature.send.value)
        assertEquals(SwapSendPhase.Review, attempt.phase)
        assertFalse(attempt.inSheet)
        assertEquals(SwapFeePlan.Preparing, attempt.plan)
        assertEquals(quoted.quote?.receivedAt, attempt.quoteReceivedAt)
        assertEquals(SwapButton.Swap(enabled = false), feature.swapButton.value)
    }

    @Test
    fun theFeeRowOpensTheSheetOnTheSameAttempt() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))

        after { sendAction(SwapAction.FeeRowTapped) }

        val opened = checkNotNull(feature.send.value)
        assertEquals(attempt.id, opened.id)
        assertTrue(opened.inSheet)
    }

    @Test
    fun anEditDropsTheBackgroundAttempt() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val first = checkNotNull(feature.send.value)

        send { sendAction(SwapAction.Amount.SetSell("2")) }
        assertNull(feature.send.value)

        after()
        assertTrue(checkNotNull(feature.send.value).id > first.id)
    }

    @Test
    fun swapWithAnUnpaidFeeOpensTheShortagePopup() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned(SwapFeeSummary.BatteryEmpty, prepared = false)
        assertEquals(SwapButton.FeeUnpaid, feature.swapButton.value)

        after { sendAction(SwapAction.Continue) }

        val attempt = checkNotNull(feature.send.value)
        assertTrue(attempt.shortagePopup)
        assertEquals(SwapSendPhase.Review, attempt.phase)

        after { sendAction(SwapAction.CloseShortage) }
        after { sendAction(SwapAction.Continue) }
        assertTrue(checkNotNull(feature.send.value).shortagePopup)
    }

    @Test
    fun swapWithAFailedPlanOpensTheSheet() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned(summary = null, prepared = false)
        assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)

        after { sendAction(SwapAction.Continue) }

        val attempt = checkNotNull(feature.send.value)
        assertTrue(attempt.inSheet)
        assertEquals(SwapSendPhase.Review, attempt.phase)
    }

    @Test
    fun swapWaitsForThePlan() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertEquals(SwapButton.Swap(enabled = false), feature.swapButton.value)

        after { sendAction(SwapAction.Continue) }

        val attempt = checkNotNull(feature.send.value)
        assertEquals(SwapSendPhase.Review, attempt.phase)
        assertFalse(attempt.inSheet)
        assertFalse(attempt.shortagePopup)
    }

    @Test
    fun swapWithAPayablePlanSends() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned(SwapFeeSummary.Charges(7))
        assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)

        after { sendAction(SwapAction.Continue) }

        assertEquals(SwapSendPhase.Working, checkNotNull(feature.send.value).phase)
    }

    @Test
    fun withTheFlagOffAQuoteStartsNoAttempt() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertNull(feature.send.value)

        after { sendAction(SwapAction.Continue) }

        assertTrue(events.single() is SwapEvent.Continue)
        assertNull(feature.send.value)
    }

    @Test
    fun aReviewReportOnAFreshAttemptKeepsTheNotice() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val first = checkNotNull(feature.send.value)
        after { sendAction(SwapAction.SendProgress(first.id, SwapSendPhase.Ended, "failed")) }
        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > first.id)

        after { sendAction(SwapAction.SendProgress(fresh.id, SwapSendPhase.Review, null)) }

        assertEquals("failed", feature.notice.value)
        assertFalse(checkNotNull(feature.send.value).inSheet)
    }

    @Test
    fun aRequoteAfterAShortageTapDoesNotReopenThePopup() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned(SwapFeeSummary.NoFunds("TON"), prepared = false)
        after { sendAction(SwapAction.Continue) }
        val shown = checkNotNull(feature.send.value)
        assertTrue(shown.shortagePopup)

        pass(2.minutes)
        assertEquals(1, quoteRequests.size)
        assertEquals(shown, feature.send.value)

        after { sendAction(SwapAction.CloseShortage) }
        after { sendAction(SwapAction.Requote) }

        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > shown.id)
        assertFalse(fresh.shortagePopup)
    }

    @Test
    fun aRequoteKeepsTheFeeMethod() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val first = checkNotNull(feature.send.value)
        after { sendAction(SwapAction.FeePicked(first.id, "ton")) }

        after { sendAction(SwapAction.Requote) }

        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > first.id)
        assertEquals("ton", fresh.feeId)
    }

    @Test
    fun anEditKeepsTheFeeMethod() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val first = checkNotNull(feature.send.value)
        after { sendAction(SwapAction.FeePicked(first.id, "ton")) }

        after { sendAction(SwapAction.Amount.SetSell("2")) }

        // As on iOS, only another asset sold drops the method picked: another amount keeps it.
        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > first.id)
        assertEquals("ton", fresh.feeId)
    }

    @Test
    fun anEndedSendStartsAFreshAttemptForTheSameQuote() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.Continue) }

        after { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Ended, null)) }

        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > attempt.id)
        assertEquals(SwapSendPhase.Review, fresh.phase)
        planned(SwapFeeSummary.Charges(7))
        assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)
        assertEquals(1, quoteRequests.size)
    }

    @Test
    fun aDisposedSendStartsNoAttempt() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.Continue) }

        after { sendAction(SwapAction.SendDisposed(attempt.id)) }

        assertNull(feature.send.value)
    }

    @Test
    fun aBlockedSendThatEndsStartsNoAttempt() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.Continue) }
        after { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Blocked, null)) }

        after { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Ended, null)) }

        assertNull(feature.send.value)
    }

    @Test
    fun aRequotedSendWaitsForTheNextQuote() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.Continue) }

        send { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Requoted, "changed")) }

        assertNull(feature.send.value)
    }

    @Test
    fun aRequotedSendKeepsTheFeeMethod() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.FeePicked(attempt.id, "ton")) }
        after { sendAction(SwapAction.Continue) }

        send { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Requoted, "changed")) }
        after { sendAction(SwapAction.Requote) }

        val fresh = checkNotNull(feature.send.value)
        assertTrue(fresh.id > attempt.id)
        assertEquals(SwapSendPhase.Review, fresh.phase)
        assertEquals("ton", fresh.feeId)
    }

    @Test
    fun anExactOutputQuoteKeepsItsAttempt() {
        env.accounts[TON_ASSET_ID] = testAccount(SwapFixtures.tonAsset, balance = "20000000000", price = "1.43")
        redesignTest {
            after { sendAction(SwapAction.Amount.SetBuy("20")) }

            assertEquals(SwapSendPhase.Review, checkNotNull(feature.send.value).phase)
            planned(SwapFeeSummary.Charges(7))
            assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)
        }
    }

    @Test
    fun anEditToTheSameAmountKeepsTheAttempt() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = checkNotNull(feature.send.value)

        after { sendAction(SwapAction.Amount.SetSell("1.0")) }

        assertEquals(attempt.id, feature.send.value?.id)
        assertEquals(1, quoteRequests.size)
    }

    @Test
    fun withTheFlagOffAQuoteLivesThirtySeconds() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }

        pass(30.seconds)
        after()

        assertEquals(2, quoteRequests.size)
    }

    @Test
    fun withTheFlagOnAQuoteLivesAMinute() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }

        pass(30.seconds)
        assertEquals(1, quoteRequests.size)
        pass(30.seconds)
        after()

        assertEquals(2, quoteRequests.size)
    }

    @Test
    fun aShortageFoundAtSendOpensThePopupAndKeepsTheSheet() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        val attempt = planned(SwapFeeSummary.Charges(7))
        after { sendAction(SwapAction.Continue) }

        after { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Review, null)) }

        val reviewed = checkNotNull(feature.send.value)
        assertEquals(attempt.id, reviewed.id)
        assertTrue(reviewed.shortagePopup)
        assertFalse(reviewed.inSheet)
    }

    @Test
    fun aSlippageChosenInTheSheetKeepsTheSheet() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        after { sendAction(SwapAction.FeeRowTapped) }

        after { sendAction(SwapAction.SelectSlippage(10)) }

        assertTrue(checkNotNull(feature.send.value).inSheet)
    }

    @Test
    fun anAmountBelowTheMinimumShowsItWhateverThePlan() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("0.5")) }
        planned(SwapFeeSummary.Charges(7))

        assertTrue(feature.swapButton.value is SwapButton.Minimum)
        after { sendAction(SwapAction.Continue) }
        assertEquals(SwapSendPhase.Review, checkNotNull(feature.send.value).phase)
    }

    @Test
    fun anAmountAboveTheBalanceSaysSo() = redesignTest {
        after { sendAction(SwapAction.Amount.SetSell("10")) }

        assertEquals(SwapButton.InsufficientBalance, feature.swapButton.value)
        assertTrue(feature.amountBlocked.value)
        assertTrue(feature.send.value != null)
    }

    @Test
    fun noRouteLeavesSwapDisabled() {
        env.quoteAnswer = { SwapFixtures.noRouteQuote }

        redesignTest {
            after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertEquals(SwapButton.Swap(enabled = false), feature.swapButton.value)
        }
    }

    @Test
    fun offlineKeepsTheLastQuoteAndDisablesSwap() = redesignTest {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned(SwapFeeSummary.Charges(7))
        env.isOnline.value = false
        env.quoteAnswer = { throw java.io.IOException("offline") }

        after { sendAction(SwapAction.Requote) }

        assertEquals(quoted.quote?.receivedAt, feature.lastQuote.value?.receivedAt)
        assertEquals(SwapButton.Swap(enabled = false), feature.swapButton.value)
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = false), feature.feeLine.value)
        assertEquals(listOf<SwapEvent>(SwapEvent.Offline), events)

        send { sendAction(SwapAction.Requote) }

        assertEquals(quoted.quote?.receivedAt, feature.shownQuote.value?.receivedAt)
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = false), feature.feeLine.value)
    }

    @Test
    fun aMultichainWalletWithAnEmptyBatterySeesThePromo() {
        env.tonWallet = WalletEntity.EMPTY.copy(id = WALLET_ID, type = WalletType.Multichain)
        env.batteryCharges = 0

        redesignTest {
            val swapped = after { sendAction(SwapAction.Asset.Swap) }

            assertEquals(USDT_TON_ASSET_ID, swapped.sellAsset?.id)
            assertEquals(SwapFeeLine.Promo, feature.feeLine.value)
        }
    }

    private fun redesignTest(body: suspend SwapScreen.() -> Unit) {
        features.set(WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED, true)
        BootFlags.update(BootFlags.values.copy(oneClickSwap = true))
        var clock = 0L
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { ++clock }
        try {
            swapTest(env, body = body)
        } finally {
            unmockkStatic(SystemClock::class)
        }
    }

    private fun SwapScreen.planned(summary: SwapFeeSummary?, prepared: Boolean = true): SwapSend {
        val attempt = checkNotNull(feature.send.value)
        val plan = summary?.let { SwapFeePlan.Ready(it, attempt.quoteReceivedAt) } ?: SwapFeePlan.Failed
        after {
            sendAction(SwapAction.FeePlanned(attempt.id, plan))
            sendAction(SwapAction.Prepared(attempt.id, prepared))
        }
        return checkNotNull(feature.send.value)
    }

    private companion object {
        private val EXACT_OUTPUT_SOURCE = BigInteger.parseString("14350324860")
    }
}

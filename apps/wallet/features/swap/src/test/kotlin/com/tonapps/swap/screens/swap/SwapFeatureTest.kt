package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.contract.UtmParameters
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

        val reset = after { env.swapRepo.notifySwapCompleted() }

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
        after { env.swapRepo.notifySwapCompleted() }

        assertEquals(listOf<SwapEvent>(SwapEvent.Completed), events)
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

    private companion object {
        private val EXACT_OUTPUT_SOURCE = BigInteger.parseString("14350324860")
    }
}

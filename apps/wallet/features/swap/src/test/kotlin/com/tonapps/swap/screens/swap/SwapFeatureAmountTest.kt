package com.tonapps.swap.screens.swap

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.CrossSwapExactType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * The amount node: what the field holds, which side it is fixed on, whether it is in fiat, and
 * whether it is still the amount MAX produced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureAmountTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    /** A keyboard that types Eastern Arabic digits still types an amount. */
    @Test
    fun nonLatinDigitsAreAnAmount() = swapTest(env) {
        val typed = after { sendAction(SwapAction.Amount.SetSell("١")) }

        assertEquals("1", typed.amountInput.text)
        assertEquals("1000000000", quoteRequests.single().sourceAmount)
    }

    /**
     * MAX never sells into the network-fee reserve: with nothing above it, there is nothing to set,
     * and the user is told what to enter instead of facing a button that does nothing.
     */
    @Test
    fun maxDoesNothingButSaysWhyWhenTheReserveTakesTheWholeBalance() {
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "500000000")

        swapTest(env) {
            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertEquals("", maxed.amountInput.text)
            assertFalse(maxed.amountInput.isMax)
            assertTrue(quoteRequests.isEmpty())
            assertEquals(listOf(SwapEvent.MaxBelowReserve(reserve = "1")), events)
        }
    }

    /** A balance exactly at the reserve leaves nothing above it either. */
    @Test
    fun maxAtTheReserveSaysWhyToo() {
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "1000000000")

        swapTest(env) {
            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertEquals("", maxed.amountInput.text)
            assertEquals(listOf(SwapEvent.MaxBelowReserve(reserve = "1")), events)
        }
    }

    /** With a balance above the reserve MAX sets the amount and has nothing to explain. */
    @Test
    fun maxAboveTheReserveSetsTheAmountQuietly() = swapTest(env) {
        val maxed = after { sendAction(SwapAction.Amount.Max) }

        assertTrue(maxed.amountInput.isMax)
        assertTrue(maxed.amountInput.text.isNotEmpty())
        assertTrue(events.toString(), events.isEmpty())
    }

    /** A fiat MAX that rounds down to no tokens at all falls back to the token amount. */
    @Test
    fun fiatMaxThatFloorsToNothingFallsBackToTokens() {
        // 1.001 TON leaves 0.001 TON above the reserve: a fraction of a cent at 1.43 USD.
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "1001000000")

        swapTest(env) {
            after { sendAction(SwapAction.Amount.ToggleCurrency) }

            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertFalse(maxed.amountInput.inFiat)
            assertTrue(maxed.amountInput.isMax)
            assertEquals("1000000", quoteRequests.single().sourceAmount)
        }
    }

    @Test
    fun maxInFiatStaysInFiat() = swapTest(env) {
        after { sendAction(SwapAction.Amount.ToggleCurrency) }

        val maxed = after { sendAction(SwapAction.Amount.Max) }

        // 4 TON above the reserve at 1.43 USD.
        assertTrue(maxed.amountInput.inFiat)
        assertTrue(maxed.amountInput.isMax)
        assertEquals("5.72", maxed.amountInput.text)
    }

    @Test
    fun typingClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val typed = after { sendAction(SwapAction.Amount.SetSell("1")) }

        assertFalse(typed.amountInput.isMax)
    }

    @Test
    fun typingTheReceiveAmountClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val typed = after { sendAction(SwapAction.Amount.SetBuy("1")) }

        assertFalse(typed.amountInput.isMax)
    }

    /** MAX was the balance of the asset being sold: another one makes it a different amount. */
    @Test
    fun newSellAssetClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val picked = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }

        assertFalse(picked.amountInput.isMax)
    }

    /** The sell side kept its asset, so MAX is still its balance. */
    @Test
    fun newBuyAssetKeepsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val picked = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }

        assertTrue(picked.amountInput.isMax)
    }

    @Test
    fun swapClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val flipped = after { sendAction(SwapAction.Asset.Swap) }

        assertFalse(flipped.amountInput.isMax)
    }

    /** A buy pick that flips the pair moves the sold asset to the other side, like Swap does. */
    @Test
    fun buyPickThatFlipsClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val flipped = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.tonAsset)) }

        assertFalse(flipped.amountInput.isMax)
    }

    @Test
    fun toggleCurrencyClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val toggled = after { sendAction(SwapAction.Amount.ToggleCurrency) }

        assertTrue(toggled.amountInput.inFiat)
        assertFalse(toggled.amountInput.isMax)
    }

    @Test
    fun completedSwapClearsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val reset = after { env.swapRepo.notifySwapCompleted() }

        assertFalse(reset.amountInput.isMax)
    }

    /** A balance refresh that leaves the rate in place leaves the amount MAX produced alone. */
    @Test
    fun refreshKeepsMax() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertTrue(refreshed.amountInput.isMax)
    }

    @Test
    fun maxReachesTheConfirmRequest() = swapTest(env) {
        val maxed = after { sendAction(SwapAction.Amount.Max) }
        assertTrue(maxed.continueEnabled)

        after { sendAction(SwapAction.Continue) }

        val swap = (events.single() as SwapEvent.Continue).request.type as ConfirmType.Swap
        assertEquals(true, swap.isMax)
    }

    /** Without a rate there is no fiat to switch to. */
    @Test
    fun toggleWithoutARateDoesNothing() {
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "5000000000", price = null)

        swapTest(env) {
            val toggled = after {
                sendAction(SwapAction.Amount.SetSell("1"))
                sendAction(SwapAction.Amount.ToggleCurrency)
            }

            assertFalse(toggled.amountInput.inFiat)
            assertEquals("1", toggled.amountInput.text)
        }
    }

    /** The toggle converts the field the user typed in, with that side's rate. */
    @Test
    fun toggleOnTheReceiveSideUsesItsRate() = swapTest(env) {
        after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }

        val toggled = after {
            sendAction(SwapAction.Amount.SetBuy("1"))
            sendAction(SwapAction.Amount.ToggleCurrency)
        }

        assertTrue(toggled.amountInput.inFiat)
        assertEquals(AmountInputSide.Buy, toggled.amountInput.side)
        // 1 ETH at the account's 2486.41 USD rate.
        assertEquals("2486.41", toggled.amountInput.text)
    }

    /** A fiat amount needs a rate: when a refresh loses it, the field falls back to tokens. */
    @Test
    fun lostRateFallsBackToTokens() = swapTest(env) {
        after {
            sendAction(SwapAction.Amount.ToggleCurrency)
            sendAction(SwapAction.Amount.Max)
        }
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "5000000000", price = null)

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertFalse(refreshed.amountInput.inFiat)
        assertFalse(refreshed.amountInput.isMax)
    }

    /** A fiat amount stays fiat while a newly picked asset is read: no account yet is not a lost rate. */
    @Test
    fun fiatAmountSurvivesAnAssetPick() {
        env.forcedAccountAnswer = { assetId ->
            if (assetId == ETH_ASSET_ID) {
                delay(ACCOUNT_LATENCY)
            }
            env.accounts[assetId]
        }

        swapTest(env) {
            after {
                sendAction(SwapAction.Amount.ToggleCurrency)
                sendAction(SwapAction.Amount.SetSell("50"))
            }

            send { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }
            val reading = pass(ACCOUNT_LATENCY / 2)
            assertTrue(reading.amountInput.inFiat)

            val read = after()
            assertTrue(read.amountInput.inFiat)
            assertEquals("50", read.amountInput.text)
        }
    }

    /** A pick that flips the pair is a flip: the amount goes with its token, as on Swap. */
    @Test
    fun pickThatFlipsMovesTheAmountWithItsToken() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }

        val flipped = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.tonAsset)) }

        assertEquals(USDT_TON_ASSET_ID, flipped.sellAsset?.id)
        assertEquals(TON_ASSET_ID, flipped.buyAsset?.id)
        assertEquals(AmountInputSide.Buy, flipped.amountInput.side)
        assertEquals("1", flipped.amountInput.text)

        val request = quoteRequests.last()
        assertEquals(CrossSwapExactType.exact_output, request.exactType)
        assertEquals(TON_ASSET_ID, request.destinationAsset)
        assertEquals("1000000000", request.destinationAmount)
    }

    /** A completed swap empties the field and puts it back on the send side, in the same currency. */
    @Test
    fun completedSwapKeepsTheCurrency() = swapTest(env) {
        after {
            sendAction(SwapAction.Amount.ToggleCurrency)
            sendAction(SwapAction.Amount.SetBuy("1"))
        }

        val reset = after { env.swapRepo.notifySwapCompleted() }

        assertEquals("", reset.amountInput.text)
        assertEquals(AmountInputSide.Sell, reset.amountInput.side)
        assertTrue(reset.amountInput.inFiat)
    }

    private fun tonAccount(balance: String, price: String? = "1.43") =
        testAccount(SwapFixtures.tonAsset, balance = balance, price = price)

    private companion object {
        val ACCOUNT_LATENCY = 400.milliseconds
    }
}
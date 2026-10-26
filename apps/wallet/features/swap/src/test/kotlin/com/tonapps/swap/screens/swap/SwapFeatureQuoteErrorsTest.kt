package com.tonapps.swap.screens.swap

import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.testing.BaseUnitTest
import io.exchangeapi.models.CrossSwapAggregator
import io.exchangeapi.models.CrossSwapProviderError
import io.exchangeapi.models.CrossSwapProviderErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything that can go wrong behind a quote: no route, a failed request, a failed config, a
 * wallet without that account. None of them may leave the screen spinning or offer a Continue that
 * has nothing to send.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureQuoteErrorsTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    /** The aggregator answered, with nothing to offer: the screen says so. */
    @Test
    fun emptyRouteListMarksTheQuoteAsNotFound() {
        env.quoteAnswer = { SwapFixtures.noRouteQuote }

        swapTest(env) {
            val noRoute = after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertTrue(noRoute.quoteUnavailable)
            assertEquals(Localization.swap_error_quote_failed, noRoute.quoteErrorMessageRes)
            assertNull(noRoute.quote)
            assertFalse(noRoute.continueEnabled)
            assertFalse(noRoute.isLoading)
        }
    }

    @Test
    fun noRouteFromTheProviderSaysThePairIsUnavailable() {
        assertNoRouteMessage(Localization.swap_error_pair_unavailable, CrossSwapProviderErrorCode.no_route)
    }

    @Test
    fun amountBelowTheProviderMinimumAsksForALargerAmount() {
        assertNoRouteMessage(Localization.swap_error_amount_too_small, CrossSwapProviderErrorCode.min_amount_not_met)
    }

    @Test
    fun unsupportedTokenSaysTheTokenIsUnavailable() {
        assertNoRouteMessage(Localization.swap_error_asset_unavailable, CrossSwapProviderErrorCode.asset_not_supported)
    }

    @Test
    fun providerDownAsksToTryAgain() {
        assertNoRouteMessage(Localization.swap_error_provider_unavailable, CrossSwapProviderErrorCode.provider_unavailable)
    }

    @Test
    fun theMostActionableProviderReasonWins() {
        assertNoRouteMessage(
            Localization.swap_error_amount_too_small,
            CrossSwapProviderErrorCode.provider_error,
            CrossSwapProviderErrorCode.no_route,
            CrossSwapProviderErrorCode.min_amount_not_met,
        )
    }

    @Test
    fun regionBlockOutranksEveryOtherReason() {
        assertNoRouteMessage(
            Localization.swap_error_region_unavailable,
            CrossSwapProviderErrorCode.no_route,
            CrossSwapProviderErrorCode.country_blocked,
        )
    }

    @Test
    fun noRouteWithoutAReasonAsksToTryAgain() {
        assertNoRouteMessage(Localization.swap_error_quote_failed)
    }

    @Test
    fun notFoundClearsOnTheNextQuote() {
        env.quoteAnswer = { SwapFixtures.noRouteQuote }

        swapTest(env) {
            val noRoute = after { sendAction(SwapAction.Amount.SetSell("1")) }
            assertTrue(noRoute.quoteUnavailable)

            val quoted = after {
                env.quoteAnswer = { SwapFixtures.exactInputQuote }
                sendAction(SwapAction.Amount.SetSell("2"))
            }

            assertFalse(quoted.quoteUnavailable)
            assertTrue(quoted.hasQuote)
        }
    }

    /**
     * A failed request reads like a missing route, so the screen never sits on an empty receive
     * field with nothing to explain it, and it must not offer a Continue or keep spinning.
     */
    @Test
    fun failedQuoteRequestShowsTheQuoteAsUnavailable() {
        env.quoteAnswer = { throw IOException("offline") }

        swapTest(env) {
            val typed = send { sendAction(SwapAction.Amount.SetSell("1")) }
            assertTrue("the spinner covers the debounce", typed.isLoading)

            val failed = after()

            assertFalse(failed.isLoading)
            assertNull(failed.quote)
            assertTrue(failed.quoteUnavailable)
            assertEquals(Localization.swap_error_quote_failed, failed.quoteErrorMessageRes)
            assertFalse(failed.continueEnabled)
        }
    }

    @Test
    fun failedQuoteRequestWhileOfflineSaysThereIsNoInternet() {
        env.isOnline.value = false
        env.quoteAnswer = { throw IOException("offline") }

        swapTest(env) {
            val failed = after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertEquals(Localization.no_internet_connection, failed.quoteErrorMessageRes)
            assertFalse(failed.continueEnabled)
        }
    }

    @Test
    fun failedQuoteRequestIsRetriedBeforeTheQuoteTtl() {
        env.quoteAnswer = { throw IOException("offline") }

        swapTest(env) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }
            assertEquals(1, quoteRequests.size)

            env.quoteAnswer = { SwapFixtures.exactInputQuote }
            pass(timings.failedQuoteRetryDelay)
            val recovered = after()

            assertEquals(2, quoteRequests.size)
            assertFalse(recovered.quoteUnavailable)
            assertTrue(recovered.continueEnabled)
        }
    }

    @Test
    fun missingRouteIsRetriedOnlyOnTheQuoteTtl() {
        env.quoteAnswer = { SwapFixtures.noRouteQuote }

        swapTest(env) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }

            val waiting = pass(timings.failedQuoteRetryDelay * 2)
            assertEquals(1, quoteRequests.size)
            assertTrue(waiting.quoteUnavailable)

            pass(timings.quoteTtl)
            after()

            assertEquals(2, quoteRequests.size)
        }
    }

    @Test
    fun quoteRecoversAfterAFailedRequest() {
        env.quoteAnswer = { throw IOException("offline") }

        swapTest(env) {
            val failed = after { sendAction(SwapAction.Amount.SetSell("1")) }
            assertNull(failed.quote)

            val recovered = after {
                env.quoteAnswer = { SwapFixtures.exactInputQuote }
                sendAction(SwapAction.Amount.SetSell("2"))
            }

            assertTrue(recovered.continueEnabled)
        }
    }

    /** The wallet has no account on that chain: nothing to quote, nothing to spin for. */
    @Test
    fun missingAccountLeavesNothingToQuote() {
        env.accountAnswer = { assetId -> env.accounts[assetId].takeIf { assetId != USDT_TON_ASSET_ID } }

        swapTest(env) {
            val typed = after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertEquals(SwapInitState.Ready, typed.initState)
            assertEquals(USDT_TON_ASSET_ID, typed.buyAsset?.id)
            assertNull(typed.buyAccount)
            assertNull(typed.quote)
            assertFalse(typed.isLoading)
            assertFalse(typed.continueEnabled)
            assertTrue(quoteRequests.isEmpty())
        }
    }

    /** A failed config is not a dead end: the repository's fallback pair is a working screen. */
    @Test
    fun failedConfigFallsBackToTheDefaultPair() {
        env.configAnswer = { throw IOException("offline") }

        swapTest(env) {
            val opened = now()

            assertEquals(SwapInitState.Ready, opened.initState)
            assertEquals(ETH_ASSET_ID, opened.sellAsset?.id)
            assertEquals(TON_ASSET_ID, opened.buyAsset?.id)
        }
    }

    /**
     * exact_output prices the send side from the route, so a route that came back without one is
     * not a quote at all — it has to read as "no route", never as a swap with an empty amount.
     */
    @Test
    fun exactOutputRouteWithoutASourceAmountIsNotFound() {
        env.quoteAnswer = {
            val quote = SwapFixtures.exactOutputQuote
            quote.copy(routes = quote.routes.map { it.copy(sourceAmount = null) })
        }

        swapTest(env) {
            val noRoute = after { sendAction(SwapAction.Amount.SetBuy("20")) }

            assertTrue(noRoute.quoteUnavailable)
            assertNull(noRoute.quote)
            assertNull(noRoute.sellAmount)
            assertFalse(noRoute.continueEnabled)
            assertFalse(noRoute.isLoading)
        }
    }

    /** The wallet is gone by the time Continue is tapped: an error, not a silent dead button. */
    @Test
    fun continueGoesForTheWalletTheScreenOpenedFor() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertTrue(quoted.continueEnabled)

        // The accounts and the quote are this wallet's: the request must not pick up another one.
        send {
            env.walletIdAnswer = { null }
            sendAction(SwapAction.Continue)
        }
        val submitted = pass(1.milliseconds)

        val request = (events.single() as SwapEvent.Continue).request
        assertEquals(SwapFixtures.WALLET_ID, request.data.walletId)
        assertFalse(submitted.continueLoading)
    }

    @Test
    fun retryReloadsTheConfigAfterAnError() {
        // No selected wallet: nothing to quote for, and the screen offers Retry.
        var walletId: String? = null
        env.walletIdAnswer = { walletId }

        swapTest(env, awaitReady = false) {
            val broken = now()
            assertEquals(SwapInitState.Error, broken.initState)
            assertNull(broken.sellAsset)

            val retried = after {
                walletId = SwapFixtures.WALLET_ID
                sendAction(SwapAction.Retry)
            }

            assertEquals(SwapInitState.Ready, retried.initState)
            assertEquals(TON_ASSET_ID, retried.sellAsset?.id)
        }
    }

    private fun assertNoRouteMessage(expected: Int, vararg codes: CrossSwapProviderErrorCode) {
        env.quoteAnswer = {
            SwapFixtures.noRouteQuote.copy(
                providerErrors = codes.map { code ->
                    CrossSwapProviderError(aggregator = CrossSwapAggregator.swapsxyz, code = code, message = code.value)
                },
            )
        }

        swapTest(env) {
            val noRoute = after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertEquals(expected, noRoute.quoteErrorMessageRes)
            assertNull(noRoute.quote)
            assertFalse(noRoute.continueEnabled)
        }
    }
}

package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.exchange.SwapConfig
import com.tonapps.wallet.data.multichain.exchange.SwapDefaultPair
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import io.exchangeapi.models.CrossSwapExactType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the graph must not do when the next input arrives before the previous one finished.
 *
 * These are the tests that own their timing: [SwapScreen.send] and [SwapScreen.pass] decide when a
 * request is dispatched and when its answer comes back, and interleave the next action in between.
 *
 * Every latency here is picked against [SwapTimings]: a stage only counts as raced if it outlasts
 * the quote debounce. A shorter one settles inside the window, the debounce restarts on the settled
 * values, and the half-applied request the test is looking for is never sent — the test would pass
 * without the graph doing anything right.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureRaceTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    private val slowQuoteRequests = mutableListOf<String>()

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    /** Typing is a burst of actions; the debounce plus latest-cancellation must spend one quote. */
    @Test
    fun typingBurstSpendsOneQuote() = raceTest {
        send { sendAction(SwapAction.Amount.SetSell("1")) }
        pass(KEYSTROKE)
        send { sendAction(SwapAction.Amount.SetSell("1.")) }
        pass(KEYSTROKE)
        send { sendAction(SwapAction.Amount.SetSell("1.2")) }
        pass(KEYSTROKE)
        after { sendAction(SwapAction.Amount.SetSell("1.25")) }

        assertEquals(quoteRequests.map { it.sourceAmount }.toString(), 1, quoteRequests.size)
        assertEquals("1250000000", quoteRequests.single().sourceAmount)
    }

    /** A pair change inside the debounce window must not spend a quote on the pair being left. */
    @Test
    fun pairChangeDuringTheDebounceSpendsNoQuote() = raceTest {
        send { sendAction(SwapAction.Amount.SetSell("1")) }
        // Right up to the edge of the window: [pass] stops just before the debounce comes due, so
        // the loader is still inside its delay.
        pass(timings.quoteDebounce)
        assertTrue(quoteRequests.isEmpty())

        val repriced = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }

        assertEquals(ETH_ASSET_ID, quoteRequests.single().destinationAsset)
        assertEquals(ETH_ASSET_ID, repriced.buyAsset?.id)
    }

    /**
     * The request for the old pair is already out when the user moves on. The graph cancels it
     * where it stands, so its answer is never built — and nothing about the pair being left ever
     * reaches the screen.
     */
    @Test
    fun lateQuoteForTheReplacedPairIsDiscarded() {
        env.swapRepo = slowRepo()

        raceTest {
            val quotes = record(feature.quote)

            send { sendAction(SwapAction.Amount.SetSell("1")) }
            // Far enough for the request to go out, not far enough for its answer to come back.
            val inFlight = pass(timings.quoteDebounce + QUOTE_LATENCY / 2)
            assertEquals(listOf(USDT_TON_ASSET_ID), slowQuoteRequests)
            assertNull("the answer is still in flight", inFlight.quote)

            val repriced = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }

            assertEquals(listOf(USDT_TON_ASSET_ID, ETH_ASSET_ID), slowQuoteRequests)
            assertEquals(ETH_ASSET_ID, repriced.buyAsset?.id)
            assertEquals(FRESH_ROUTE_ID, repriced.quote?.quote?.routeId)
            assertTrue(
                "the quote for the replaced pair reached the screen",
                quotes.none { it?.quote?.routeId == STALE_ROUTE_ID },
            )
        }
    }

    /** Flipping must not quote the pair the user just left, however slowly the accounts are read. */
    @Test
    fun swapSidesDuringAccountReloadNeverQuotesTheOldPair() {
        env.accountAnswer = { assetId ->
            delay(ACCOUNT_LATENCY)
            env.accounts[assetId]
        }

        raceTest {
            after { sendAction(SwapAction.Amount.SetSell("1")) }
            assertEquals(1, quoteRequests.size)

            after { sendAction(SwapAction.Asset.Swap) }

            // The reload outlasts the debounce, so a graph that quoted the accounts still on screen
            // would have spent one on the old pair before the new one resolved. Before the flip the
            // fixed side was Send (TON); after it, Receive. A request selling TON by exact_output is
            // that half-applied flip.
            assertTrue(
                quoteRequests.map { "${it.sourceAsset}->${it.destinationAsset}:${it.exactType}" }.toString(),
                quoteRequests.none {
                    it.sourceAsset == TON_ASSET_ID && it.exactType == CrossSwapExactType.exact_output
                },
            )
            val last = quoteRequests.last()
            assertEquals(USDT_TON_ASSET_ID, last.sourceAsset)
            assertEquals(TON_ASSET_ID, last.destinationAsset)
        }
    }

    /** Continue is not re-entrant: a double tap must produce one confirm request, not two. */
    @Test
    fun doubleTapOnContinueEmitsOneEvent() = raceTest {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertTrue(quoted.continueEnabled)

        // The confirm request is built on the IO dispatcher, so the second tap lands while the first
        // one is still being prepared.
        send {
            sendAction(SwapAction.Continue)
            sendAction(SwapAction.Continue)
        }
        val submitted = pass(1.milliseconds)

        assertEquals(events.toString(), 1, events.size)
        assertFalse(submitted.continueLoading)
    }

    /**
     * A race leaves its mark on the states in between, not only on the one the screen settles on,
     * so every frame the screen rendered on the way is held to [SwapStateSnapshot.assertCoherent].
     */
    private fun raceTest(body: suspend SwapScreen.() -> Unit) = swapTest(env) {
        body()
        rendered.forEach { it.assertCoherent() }
    }

    /** Answers each pair with its own route, late enough to still be in flight. */
    private fun slowRepo(): SwapRepository = mockk<SwapRepository>().also { repo ->
        every { repo.swapCompleted } returns MutableSharedFlow()
        // Without a config the screen would open on the feature's ETH -> TON fallback, where
        // picking ETH to buy flips the pair instead of replacing the side.
        coEvery { repo.getConfig(any(), any(), any()) } returns SwapConfig(
            defaultPair = SwapDefaultPair(
                source = SwapFixtures.tonAsset,
                destination = SwapFixtures.usdtTonAsset,
            ),
            slippagePerChain = emptyMap(),
        )
        every { repo.formatRateLabel(any(), any(), any()) } returns "1 TON"
        every { repo.getMaxReserve(any()) } answers {
            firstArg<Asset>().decimals.baseUnit(BigInteger.ZERO)
        }
        coEvery { repo.fetchQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
            val destinationAssetId = secondArg<AccountWithDetails>().asset.id
            slowQuoteRequests += destinationAssetId
            delay(QUOTE_LATENCY)
            SwapQuoteResult.Found(quoteFor(destinationAssetId))
        }
    }

    private fun quoteFor(destinationAssetId: String) = SwapQuote(
        routeId = when (destinationAssetId) {
            ETH_ASSET_ID -> FRESH_ROUTE_ID
            else -> STALE_ROUTE_ID
        },
        sourceBaseAmount = BigInteger.parseString("1000000000"),
        buyBaseAmount = BigInteger.parseString("1417408"),
        minimumBuyBaseAmount = BigInteger.parseString("1403233"),
        slippageBps = 100,
        priceImpactBps = null,
        provider = SwapQuote.Provider.SwapXyz,
    )

    private companion object {
        val KEYSTROKE = 120.milliseconds

        // Both outlast SwapTimings.quoteDebounce: a stage that settles inside the debounce window
        // is never raced against, because the window restarts on the values it settled on.
        val ACCOUNT_LATENCY = 900.milliseconds
        val QUOTE_LATENCY = 900.milliseconds

        const val STALE_ROUTE_ID = "route-for-the-pair-left-behind"
        const val FRESH_ROUTE_ID = "route-for-the-pair-in-front"
    }
}

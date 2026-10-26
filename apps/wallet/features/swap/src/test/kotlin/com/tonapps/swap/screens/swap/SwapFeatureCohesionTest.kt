package com.tonapps.swap.screens.swap

import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * The nodes the screen reads must agree with each other in every state the screen can render.
 *
 * Each step is one action and the screen it settled on; [SwapStateSnapshot.assertCoherent] runs on
 * every one of them, so a step below asserts only what that action was supposed to change. The
 * states the graph passes through *between* two actions are recorded separately and checked at the
 * end — that is where a half-applied combination would hide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureCohesionTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
        // A little latency on the accounts so the "an asset is still resolving" states are real.
        env.accountAnswer = { assetId ->
            delay(ACCOUNT_LATENCY)
            env.accounts[assetId]
        }
    }

    @Test
    fun everyRenderedStateAgreesWithItself() = swapTest(env) {
        val opened = now()
        assertEquals(SwapInitState.Ready, opened.initState)
        assertNull(opened.quote)

        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        assertTrue(quoted.hasQuote)
        assertTrue(quoted.continueEnabled)

        // A pair change reads the new asset's account, so this one settles through a state with no
        // buy side.
        val repriced = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        assertEquals(ETH_ASSET_ID, repriced.buyAsset?.id)
        assertTrue(repriced.hasQuote)

        val overBalance = after { sendAction(SwapAction.Amount.SetSell("50")) }
        assertTrue(overBalance.insufficientFunds)
        assertFalse(overBalance.continueEnabled)

        val noRoute = after {
            env.quoteAnswer = { SwapFixtures.noRouteQuote }
            sendAction(SwapAction.Amount.SetSell("3"))
        }
        assertTrue(noRoute.quoteUnavailable)
        assertFalse(noRoute.continueEnabled)

        val quotable = after {
            env.quoteAnswer = { SwapFixtures.exactInputQuote }
            sendAction(SwapAction.Amount.SetSell("1"))
        }
        assertTrue(quotable.continueEnabled)

        val submitted = after { sendAction(SwapAction.Continue) }
        assertEquals(1, events.size)
        assertFalse(submitted.continueLoading)

        rendered.forEach { it.assertCoherent() }

        // The invariants are only worth anything if the script reached those states.
        assertTrue("no loading state observed", rendered.any { it.isLoading })
        assertTrue("no quote observed", rendered.any { it.hasQuote })
        assertTrue("no enabled state observed", rendered.any { it.continueEnabled })
        assertTrue("no insufficient funds observed", rendered.any { it.insufficientFunds })
        assertTrue("no missing route observed", rendered.any { it.quoteUnavailable })
        assertTrue("no opening state observed", rendered.any { !it.hasAccounts })
    }

    private companion object {
        val ACCOUNT_LATENCY = 150.milliseconds
    }
}

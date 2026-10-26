package com.tonapps.swap.screens.swap

import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
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
 * The account node: one account per asset of the pair, read when the asset joins it and on every
 * balance refresh, never per side. What a side shows is the account of the asset it holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureAccountsTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    @Test
    fun pickReadsOnlyTheAssetThatJoinedThePair() = swapTest(env) {
        env.forcedAccountReads.clear()

        val picked = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }

        assertEquals(listOf(ETH_ASSET_ID), env.forcedAccountReads.toList())
        assertEquals(TON_ASSET_ID, picked.sellAccount?.asset?.id)
        assertEquals(ETH_ASSET_ID, picked.buyAccount?.asset?.id)
    }

    /** Picking the other side's asset flips the pair: no asset joins it, so nothing is read. */
    @Test
    fun pickThatFlipsThePairReadsNothing() = swapTest(env) {
        val before = now()
        env.forcedAccountReads.clear()

        val flipped = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.tonAsset)) }

        assertTrue(env.forcedAccountReads.isEmpty())
        assertEquals(before.sellAccount, flipped.buyAccount)
        assertEquals(before.buyAccount, flipped.sellAccount)
    }

    /** An asset that left the pair and came back is read again: its old account may be stale. */
    @Test
    fun assetPickedAgainIsReadAgain() = swapTest(env) {
        env.forcedAccountReads.clear()

        after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.usdtTonAsset)) }

        assertEquals(listOf(ETH_ASSET_ID, USDT_TON_ASSET_ID), env.forcedAccountReads.toList())
    }

    /** A refresh re-reads the assets in the pair, not the ones the user has moved away from. */
    @Test
    fun refreshReadsOnlyTheAssetsInThePair() = swapTest(env) {
        after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        env.forcedAccountReads.clear()

        after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals(setOf(TON_ASSET_ID, ETH_ASSET_ID), env.forcedAccountReads.toSet())
    }

    /**
     * While the new asset is read the side has no account: it never shows the balance of the asset
     * it replaced, and the screen is loading rather than quoting.
     */
    @Test
    fun sideShowsNoAccountWhileItsNewAssetIsRead() {
        env.forcedAccountAnswer = { assetId ->
            if (assetId == ETH_ASSET_ID) {
                delay(ACCOUNT_LATENCY)
            }
            env.accounts[assetId]
        }

        swapTest(env) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }

            send { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
            val reading = pass(ACCOUNT_LATENCY / 2)

            assertEquals(ETH_ASSET_ID, reading.buyAsset?.id)
            assertNull(reading.buyAccount)
            assertTrue(reading.isLoading)
            assertNull(reading.quote)
            assertFalse(reading.continueEnabled)
            assertEquals(1, quoteRequests.size)

            val read = after()

            assertEquals(ETH_ASSET_ID, read.buyAccount?.asset?.id)
            assertTrue(read.hasQuote)
            assertEquals(ETH_ASSET_ID, quoteRequests.last().destinationAsset)
        }
    }

    /** MAX works from the account of the asset on screen, never from the one it replaced. */
    @Test
    fun maxWaitsForTheNewSellAccount() {
        env.forcedAccountAnswer = { assetId ->
            if (assetId == ETH_ASSET_ID) {
                delay(ACCOUNT_LATENCY)
            }
            env.accounts[assetId]
        }

        swapTest(env) {
            send { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }
            pass(ACCOUNT_LATENCY / 2)

            val early = send { sendAction(SwapAction.Amount.Max) }
            assertEquals("", early.amountInput.text)
            assertFalse(early.amountInput.isMax)

            after()
            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertTrue(maxed.amountInput.isMax)
            // ETH is not TON: no reserve is kept aside, the whole 0.1 ETH goes.
            assertEquals("100000000000000000", quoteRequests.last().sourceAmount)
        }
    }

    /** The side shows the asset as the swap backend described it, icon included. */
    @Test
    fun sideKeepsTheImageThePickedAssetCarries() = swapTest(env) {
        val image = "https://swap.example/eth.png"

        val picked = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset.copy(imageUrl = image))) }

        assertEquals(image, picked.sellAccount?.asset?.imageUrl)
    }

    /** A picked asset without an icon of its own leaves the wallet's icon in place. */
    @Test
    fun blankImageKeepsTheWalletsOwn() = swapTest(env) {
        val picked = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset.copy(imageUrl = ""))) }

        assertEquals(SwapFixtures.ethAccount.asset.imageUrl, picked.sellAccount?.asset?.imageUrl)
    }

    /** A read that throws is a side with no account, not a broken screen. */
    @Test
    fun readThatThrowsLeavesTheSideEmpty() {
        env.forcedAccountAnswer = { assetId ->
            if (assetId == ETH_ASSET_ID) {
                throw IllegalStateException("balance endpoint is down")
            }
            env.accounts[assetId]
        }

        swapTest(env) {
            val picked = after {
                sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset))
                sendAction(SwapAction.Amount.SetSell("1"))
            }

            assertEquals(SwapInitState.Ready, picked.initState)
            assertEquals(ETH_ASSET_ID, picked.buyAsset?.id)
            assertNull(picked.buyAccount)
            assertEquals(TON_ASSET_ID, picked.sellAccount?.asset?.id)
            assertFalse(picked.isLoading)
            assertTrue(quoteRequests.isEmpty())
        }
    }

    /** A wallet that has never refreshed has nothing to replay: its first refresh is a real one. */
    @Test
    fun firstRefreshIsReadWhenNoneWasReplayed() {
        env = SwapTestEnv(context, timeSource, features, refreshReplayed = false)

        swapTest(env) {
            env.forcedAccountReads.clear()
            env.accounts[TON_ASSET_ID] = testTonAccount(balance = "1000000000")

            val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

            assertEquals(setOf(TON_ASSET_ID, USDT_TON_ASSET_ID), env.forcedAccountReads.toSet())
            assertEquals("1000000000", refreshed.sellAccount?.balance?.available)
        }
    }

    /** A refresh that lands while the previous read is in flight replaces it: only the latest lands. */
    @Test
    fun burstOfRefreshesLandsOnlyTheLatestRead() = swapTest(env) {
        var tonReads = 0
        env.forcedAccountAnswer = { assetId ->
            when (assetId) {
                TON_ASSET_ID -> {
                    tonReads++
                    val balance = "${tonReads}000000000"
                    delay(ACCOUNT_LATENCY)
                    testTonAccount(balance = balance)
                }

                else -> env.accounts[assetId]
            }
        }
        val balances = record(feature.sellAccount)

        send { env.refreshTrigger.tryEmit(Unit) }
        pass(ACCOUNT_LATENCY / 4)
        send { env.refreshTrigger.tryEmit(Unit) }
        pass(ACCOUNT_LATENCY / 4)
        send { env.refreshTrigger.tryEmit(Unit) }
        // A refresh reads in the background, never as a Loading, so there is nothing to settle on.
        val refreshed = pass(ACCOUNT_LATENCY * 2)

        assertEquals(3, tonReads)
        assertEquals("3000000000", refreshed.sellAccount?.balance?.available)
        assertTrue(
            balances.map { it?.balance?.available }.toString(),
            balances.none { it?.balance?.available in setOf("1000000000", "2000000000") },
        )
    }

    private fun testTonAccount(balance: String) = testAccount(SwapFixtures.tonAsset, balance = balance, price = "1.43")

    private companion object {
        val ACCOUNT_LATENCY = 400.milliseconds
    }
}

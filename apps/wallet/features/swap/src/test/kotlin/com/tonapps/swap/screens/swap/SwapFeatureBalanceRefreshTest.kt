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

@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureBalanceRefreshTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    @Test
    fun openingTheScreenReadsEachSideOncePastTheCache() {
        env.forcedAccountAnswer = { assetId ->
            when (assetId) {
                TON_ASSET_ID -> tonAccount(balance = "1000000000")
                else -> env.accounts[assetId]
            }
        }

        swapTest(env) {
            assertEquals("1000000000", now().sellAccount?.balance?.available)
            assertEquals(listOf(TON_ASSET_ID, USDT_TON_ASSET_ID).sorted(), env.accountReads.sorted())
            assertEquals(env.accountReads.sorted(), env.forcedAccountReads.sorted())
        }
    }

    @Test
    fun selectedAssetIsReadPastTheCache() {
        env.forcedAccountAnswer = { assetId ->
            when (assetId) {
                ETH_ASSET_ID -> ethAccount(balance = "1")
                else -> env.accounts[assetId]
            }
        }

        swapTest(env) {
            val selected = after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }

            assertEquals("1", selected.sellAccount?.balance?.available)
        }
    }

    /** Switching trades the sides' accounts: each balance moves with its asset, nothing is re-read. */
    @Test
    fun switchMovesTheAccountsWithTheirAssets() = swapTest(env) {
        val before = now()
        env.forcedAccountReads.clear()

        val switched = after { sendAction(SwapAction.Asset.Swap) }

        assertEquals(before.buyAccount, switched.sellAccount)
        assertEquals(before.sellAccount, switched.buyAccount)
        assertTrue(env.forcedAccountReads.isEmpty())
    }

    @Test
    fun balanceRefreshRereadsBothSidesPastTheCache() = swapTest(env) {
        env.forcedAccountReads.clear()
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "1000000000")

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals("1000000000", refreshed.sellAccount?.balance?.available)
        assertEquals(setOf(TON_ASSET_ID, USDT_TON_ASSET_ID), env.forcedAccountReads.toSet())
    }

    @Test
    fun balanceRefreshKeepsTheQuoteItAlreadyHas() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        val loading = record(feature.isLoading)
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "500000000")

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals(1, quoteRequests.size)
        assertEquals(quoted.quote, refreshed.quote)
        assertTrue(refreshed.insufficientFunds)
        assertFalse(loading.any { it })
    }

    @Test
    fun afterASwapTheFormShowsTheNewBalance() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        after { env.swapRepo.notifySwapCompleted() }
        env.accounts[TON_ASSET_ID] = tonAccount(balance = "4000000000")

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals("", refreshed.amountInput.text)
        assertEquals("4000000000", refreshed.sellAccount?.balance?.available)
    }

    @Test
    fun failedReloadKeepsTheAccountItHad() = swapTest(env) {
        env.forcedAccountAnswer = { null }

        val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals(TON_ASSET_ID, refreshed.sellAccount?.asset?.id)
        assertEquals(SwapFixtures.tonAccount.balance.available, refreshed.sellAccount?.balance?.available)
    }

    @Test
    fun sideThatResolvedToNothingRetriesOnRefresh() {
        env.forcedAccountAnswer = { assetId -> env.accounts[assetId].takeIf { assetId != TON_ASSET_ID } }

        swapTest(env) {
            assertNull(now().sellAccount)

            env.forcedAccountAnswer = { assetId -> env.accounts[assetId] }
            val refreshed = after { env.refreshTrigger.tryEmit(Unit) }

            assertEquals(TON_ASSET_ID, refreshed.sellAccount?.asset?.id)
        }
    }

    @Test
    fun reloadStillInFlightWhenTheAssetChangesNeverLands() {
        env.forcedAccountAnswer = { assetId ->
            if (assetId == TON_ASSET_ID) {
                delay(RELOAD_LATENCY)
            }
            env.accounts[assetId]
        }

        swapTest(env) {
            pass(RELOAD_LATENCY)
            env.forcedAccountReads.clear()

            send { env.refreshTrigger.tryEmit(Unit) }
            pass(RELOAD_LATENCY / 2)
            assertTrue(TON_ASSET_ID in env.forcedAccountReads)

            after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }
            val landed = pass(RELOAD_LATENCY)

            assertEquals(ETH_ASSET_ID, landed.sellAsset?.id)
            assertEquals(ETH_ASSET_ID, landed.sellAccount?.asset?.id)
        }
    }

    private fun tonAccount(balance: String) = testAccount(SwapFixtures.tonAsset, balance = balance, price = "1.43")

    private fun ethAccount(balance: String) = testAccount(SwapFixtures.ethAsset, balance = balance, price = "2486.41")

    private companion object {
        val RELOAD_LATENCY = 500.milliseconds
    }
}

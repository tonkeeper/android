package com.tonapps.swap.screens.swap

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.swap.SwapRoutes
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TRON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.WALLET_ID
import com.tonapps.wallet.data.multichain.legacy.LegacySwapPair
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A legacy wallet swaps GRAM ↔ USDT TRC20 on its own addresses: the pair is fixed, there is no
 * per-wallet config to read, and the swap API is asked without a wallet id the backend never issued.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureLegacyTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
        env.legacyWallet = legacyWallet
        env.legacyPair = { sell, _ ->
            when (sell) {
                USDT_TRON_ASSET_ID -> LegacySwapPair(sell = SwapFixtures.usdtTronAsset, buy = SwapFixtures.tonAsset)
                else -> LegacySwapPair(sell = SwapFixtures.tonAsset, buy = SwapFixtures.usdtTronAsset)
            }
        }
    }

    @Test
    fun opensOnTheFixedPairTheRouteAskedForWithoutAConfig() = swapTest(
        env,
        route = SwapRoutes.Swap(sellAssetId = USDT_TRON_ASSET_ID),
    ) {
        val opened = now()

        assertEquals(USDT_TRON_ASSET_ID, opened.sellAsset?.id)
        assertEquals(TON_ASSET_ID, opened.buyAsset?.id)
        assertTrue(feature.isPairLocked.value)
        assertTrue(env.configRequests.isEmpty())
    }

    @Test
    fun quotesWithoutAWalletId() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }

        assertEquals(listOf<String?>(null), env.quoteWalletIds.toList())
    }

    @Test
    fun pickedAssetsCannotReplaceThePairButTheSidesStillFlip() = swapTest(env) {
        val picked = after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        assertEquals(USDT_TRON_ASSET_ID, picked.buyAsset?.id)

        val flipped = after { sendAction(SwapAction.Asset.Swap) }
        assertEquals(USDT_TRON_ASSET_ID, flipped.sellAsset?.id)
        assertEquals(TON_ASSET_ID, flipped.buyAsset?.id)
    }

    /** GRAM keeps the same 1 GRAM fee reserve a multichain wallet does: below it MAX only explains. */
    @Test
    fun maxOnGramBelowTheReserveSaysWhy() {
        env.accounts[TON_ASSET_ID] = testAccount(SwapFixtures.tonAsset, balance = "800000000", price = "1.43")

        swapTest(env) {
            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertEquals("", maxed.amountInput.text)
            assertFalse(maxed.amountInput.isMax)
            assertEquals(listOf(SwapEvent.MaxBelowReserve(reserve = "1")), events)
        }
    }

    /** USDT TRC20 has no reserve: MAX sells the whole balance and says nothing. */
    @Test
    fun maxOnUsdtSellsTheWholeBalance() = swapTest(
        env,
        route = SwapRoutes.Swap(sellAssetId = USDT_TRON_ASSET_ID),
    ) {
        val maxed = after { sendAction(SwapAction.Amount.Max) }

        assertEquals("100", maxed.amountInput.text)
        assertTrue(maxed.amountInput.isMax)
        assertEquals("100000000", quoteRequests.single().sourceAmount)
        assertTrue(events.toString(), events.isEmpty())
    }

    @Test
    fun multichainWalletStillQuotesWithItsWalletId() {
        env.legacyWallet = null

        swapTest(env) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }

            assertFalse(feature.isPairLocked.value)
            assertEquals(listOf<String?>(WALLET_ID), env.quoteWalletIds.toList())
        }
    }

    private companion object {
        val legacyWallet = LegacySwapWallet(
            id = "legacy",
            accountId = "0:legacy",
            name = "legacy_wallet",
            emoji = "",
            tonAddress = SwapFixtures.TON_ADDRESS,
            tonPublicKey = "0x00",
            tonAddressType = Address.Type.TonV5R1,
            tronAddress = SwapFixtures.TRON_ADDRESS,
        )
    }
}

package com.tonapps.swap.screens.swap

import android.os.SystemClock
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import io.batteryapi.models.NetworkFee
import io.batteryapi.models.NetworkFeeAmounts
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.batteryapi.models.NetworkFees as NetworkFeesResponse

/**
 * MAX and its percents keep the same reserve: a fixed 1 TON for the TON coin whatever Battery's `/network-fees`
 * says, `swap.safe` for another asset whose fee is paid in itself, nothing for the rest. The reserve is not a
 * minimum: only the TON coin has one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureNetworkFeeReserveTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
        env.networkFees = NetworkFeesResponse(assets = listOf(tonCoinFee, usdtTonFee, ethCoinFee))
    }

    /** 0.1 ETH less the 0.001 ETH safe fee. */
    @Test
    fun maxSellsTheBalanceLessTheSafeFee() = sellingEth {
        val maxed = after { sendAction(SwapAction.Amount.Max) }

        assertTrue(maxed.amountInput.isMax)
        assertEquals("99000000000000000", quoteRequests.last().sourceAmount)
    }

    /** The send found the fee, paid in ETH, leaves less than the max: that is the max from here on. */
    @Test
    fun aMaxTheFeeLeavesNoRoomForIsFittedToWhatIsLeft() = sendingInPlace {
        after { sendAction(SwapAction.Amount.Max) }
        val attempt = checkNotNull(feature.send.value)

        val fitted = after { sendAction(SwapAction.FitMax(attempt.id, BigInteger.parseString("97500000000000000"))) }

        assertTrue(fitted.amountInput.isMax)
        assertEquals("0.0975", fitted.amountInput.text)
        assertEquals("97500000000000000", quoteRequests.last().sourceAmount)
    }

    /** A fit is its attempt's, for the max it was found for: one of another attempt, or one that would raise the max, is dropped. */
    @Test
    fun aFitOfAnotherAttemptOrOneThatRaisesTheMaxIsDropped() = sendingInPlace {
        after { sendAction(SwapAction.Amount.Max) }
        val attempt = checkNotNull(feature.send.value)

        val stale = after { sendAction(SwapAction.FitMax(attempt.id + 1, BigInteger.parseString("97500000000000000"))) }
        assertEquals("0.099", stale.amountInput.text)

        val raised = after { sendAction(SwapAction.FitMax(attempt.id, BigInteger.parseString("99500000000000000"))) }
        assertEquals("0.099", raised.amountInput.text)
    }

    /** The attempt that gave way to be quoted again still says what to quote: its fit is taken. */
    @Test
    fun aFitOfTheAttemptThatGaveWayIsTaken() = sendingInPlace {
        after { sendAction(SwapAction.Amount.Max) }
        val attempt = checkNotNull(feature.send.value)
        after { sendAction(SwapAction.SendProgress(attempt.id, SwapSendPhase.Requoted, null)) }

        val fitted = after { sendAction(SwapAction.FitMax(attempt.id, BigInteger.parseString("97500000000000000"))) }

        assertEquals("0.0975", fitted.amountInput.text)
        assertTrue(fitted.amountInput.isMax)
    }

    /** An amount typed since is another attempt's: the fit found for the max does not replace it. */
    @Test
    fun aFitDoesNotReplaceAnAmountTypedSince() = sendingInPlace {
        after { sendAction(SwapAction.Amount.Max) }
        val attempt = checkNotNull(feature.send.value)
        after { sendAction(SwapAction.Amount.SetSell("0.05")) }

        val typed = after { sendAction(SwapAction.FitMax(attempt.id, BigInteger.parseString("40000000000000000"))) }

        assertEquals("0.05", typed.amountInput.text)
        assertFalse(typed.amountInput.isMax)
    }

    @Test
    fun aPercentIsTakenOfTheSameMax() = sellingEth {
        val half = after { sendAction(SwapAction.Amount.Percent(50)) }

        assertFalse(half.amountInput.isMax)
        assertEquals("49500000000000000", quoteRequests.last().sourceAmount)
    }

    @Test
    fun maxOfABalanceUnderTheSafeFeeSaysTheCoinIsShortForTheFee() {
        env.accounts[ETH_ASSET_ID] = testAccount(SwapFixtures.ethAsset, balance = "500000000000000", price = "2486.41")

        sellingEth {
            val maxed = after { sendAction(SwapAction.Amount.Max) }

            assertEquals("", maxed.amountInput.text)
            assertEquals(listOf(SwapEvent.MaxBelowFee(symbol = "ETH")), events)
        }
    }

    @Test
    fun anAmountBelowTheSafeFeeIsNoMinimum() = sellingEth {
        after { sendAction(SwapAction.Amount.SetSell("0.0009")) }

        assertFalse(feature.swapButton.value.toString(), feature.swapButton.value is SwapButton.Minimum)
    }

    /** The TON coin keeps its fixed 1 TON even with a `/network-fees` entry of its own. */
    @Test
    fun maxOfTheTonCoinKeepsTheFixedReserveWhateverTheResponseSays() = swapTest(env) {
        after { sendAction(SwapAction.Amount.Max) }

        assertEquals("4000000000", quoteRequests.single().sourceAmount)
    }

    /** A jetton's gas is paid in TON: its entry takes nothing off the jetton. */
    @Test
    fun maxOfATokenWhoseFeeIsPaidInTheCoinSellsTheWholeBalance() {
        env.accounts[USDT_TON_ASSET_ID] = testAccount(SwapFixtures.usdtTonAsset, balance = "7000000")

        swapTest(env) {
            after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.usdtTonAsset)) }

            after { sendAction(SwapAction.Amount.Max) }

            assertEquals("7000000", quoteRequests.last().sourceAmount)
        }
    }

    /** No answer from `/network-fees`: a coin other than TON keeps nothing, as before. */
    @Test
    fun withoutNetworkFeesMaxSellsTheWholeBalanceOfAnotherCoin() {
        env.networkFees = null

        sellingEth {
            after { sendAction(SwapAction.Amount.Max) }

            assertEquals("100000000000000000", quoteRequests.last().sourceAmount)
        }
    }

    private fun sellingEth(block: suspend SwapScreen.() -> Unit) = swapTest(env) {
        after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }
        block()
    }

    // Selling ETH on the screen that sends the swap itself: every quote starts a send attempt.
    private fun sendingInPlace(block: suspend SwapScreen.() -> Unit) {
        features.set(WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED, true)
        BootFlags.update(BootFlags.values.copy(oneClickSwap = true))
        var clock = 0L
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { ++clock }
        try {
            sellingEth(block)
        } finally {
            unmockkStatic(SystemClock::class)
        }
    }

    private companion object {
        val tonCoinFee = NetworkFee(
            assetId = TON_ASSET_ID,
            feeAssetId = TON_ASSET_ID,
            feeDecimals = 9,
            swap = amounts(safe = "403000000"),
            perps = amounts(safe = "1463800"),
        )
        val ethCoinFee = NetworkFee(
            assetId = ETH_ASSET_ID,
            feeAssetId = ETH_ASSET_ID,
            feeDecimals = 18,
            swap = amounts(safe = "1000000000000000"),
            perps = amounts(safe = "1843400000000000"),
        )
        val usdtTonFee = NetworkFee(
            assetId = USDT_TON_ASSET_ID,
            feeAssetId = TON_ASSET_ID,
            feeDecimals = 9,
            swap = amounts(safe = "403000000"),
            perps = amounts(safe = "403000000"),
        )

        fun amounts(safe: String) = NetworkFeeAmounts(typical = "1", high = "1", safe = safe)
    }
}

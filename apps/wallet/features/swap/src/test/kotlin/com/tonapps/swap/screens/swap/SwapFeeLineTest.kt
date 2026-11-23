package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.isRouteStale
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapNoRouteReason
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SwapFeeLineTest : BaseUnitTest() {

    private val quote = DisplayQuote(
        quote = SwapQuote(
            routeId = "route",
            sourceBaseAmount = BigInteger.ONE,
            buyBaseAmount = BigInteger.ONE,
            minimumBuyBaseAmount = BigInteger.ONE,
            slippageBps = null,
            priceImpactBps = null,
            provider = SwapQuote.Provider.Omniston,
        ),
        sellAmount = "1",
        buyAmount = "1",
        rateLabel = "",
        receivedAt = 1,
        minReceived = "1" to null,
    )

    private val offline = SwapQuoteError.Failed(isOffline = true)
    private val ready = SwapFeePlan.Ready(SwapFeeSummary.Charges(7), quoteReceivedAt = 1)

    @Test
    fun anEmptyAmountShowsThePromoOrNothing() {
        assertEquals(SwapFeeLine.Promo, line(amountEmpty = true, promo = true, quote = null))
        assertEquals(SwapFeeLine.Hidden, line(amountEmpty = true, promo = false, quote = null))
    }

    @Test
    fun aQuoteOnItsWayIsFetching() {
        assertEquals(SwapFeeLine.Fetching, line(fetching = true, quote = null, plan = null))
    }

    @Test
    fun aQuoteErrorIsNamed() {
        val error = SwapQuoteError.NotFound(SwapNoRouteReason.PairUnavailable)

        assertEquals(
            SwapFeeLine.QuoteFailed(Localization.swap_error_pair_unavailable),
            line(quoteError = error, quote = null, plan = null),
        )
    }

    @Test
    fun aPlanOnItsWayIsEstimating() {
        assertEquals(SwapFeeLine.Estimating, line(plan = SwapFeePlan.Preparing))
        assertEquals(SwapFeeLine.Estimating, line(plan = null))
    }

    @Test
    fun aFailedPlanIsAnUnknownFee() {
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Unknown, tappable = true), line(plan = SwapFeePlan.Failed))
    }

    @Test
    fun aReadyPlanShowsItsSummary() {
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = true), line(plan = ready))
    }

    @Test
    fun anUntappableButtonLeavesTheEstimateWithoutAChevron() {
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = false), line(plan = ready, tappable = false))
    }

    @Test
    fun offlineKeepsThePlanOfTheLastQuote() {
        assertEquals(SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = false), line(quoteError = offline, plan = ready, tappable = false))
    }

    @Test
    fun offlineWithoutAPlanIsABareNetworkFee() {
        assertEquals(SwapFeeLine.BareNetworkFee, line(quoteError = offline, plan = SwapFeePlan.Preparing))
    }

    @Test
    fun offlineWithoutAQuoteIsABareNetworkFee() {
        assertEquals(SwapFeeLine.BareNetworkFee, line(quoteError = offline, quote = null, plan = null))
    }

    @Test
    fun anOnlineFailureWithoutAQuoteIsNamed() {
        assertEquals(
            SwapFeeLine.QuoteFailed(Localization.swap_error_quote_failed),
            line(quoteError = SwapQuoteError.Failed(isOffline = false), quote = null, plan = null),
        )
    }

    @Test
    fun aShortRouteLivesUntilFiveSecondsBeforeItExpires() {
        assertEquals(25.seconds, quoteLifetime(arrivalLifetimeMillis = 30_000))
    }

    @Test
    fun aSevenSecondRouteRefreshesWhenSendsStop() {
        assertRefreshMatchesSendCutoff(lifetimeMillis = 7_000, expected = 5.seconds)
    }

    @Test
    fun aSeventySecondRouteRefreshesWhenSendsStop() {
        assertRefreshMatchesSendCutoff(lifetimeMillis = 70_000, expected = 60.seconds)
    }

    @Test
    fun aRouteWithoutALifetimeRefreshesWhenSendsStop() {
        assertRefreshMatchesSendCutoff(lifetimeMillis = null, expected = 60.seconds)
    }

    @Test
    fun aRouteOfFiveSecondsOrLessRefreshesWhenSendsStop() {
        assertRefreshMatchesSendCutoff(lifetimeMillis = 5_000, expected = 5.seconds)
        assertRefreshMatchesSendCutoff(lifetimeMillis = -5_000, expected = 5.seconds)
    }

    @Test
    fun theBatteryPromoIsNeverForGram() {
        assertFalse(promo(TestAssets.ton))
    }

    @Test
    fun theBatteryPromoIsShownOnlyOnce() {
        assertFalse(promo(TestAssets.usdtTon, shownBefore = true))
    }

    @Test
    fun theBatteryPromoIsForAnEmptyBatteryOnly() {
        assertFalse(promo(TestAssets.usdtTon, charges = 3))
    }

    @Test
    fun theBatteryPromoIsForATonJettonTrxTronUsdtAndARelayedEvmToken() {
        assertTrue(promo(TestAssets.usdtTon))
        assertTrue(promo(TestAssets.usdtTron))
        assertTrue(promo(TestAssets.trx))
        assertTrue(promo(TestAssets.usdtEth))
    }

    @Test
    fun theBatteryPromoSkipsATrc20OtherThanUsdt() {
        assertFalse(promo(testAsset("tron/mainnet/trc20/TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8", "USDC", decimals = 6)))
    }

    @Test
    fun theBatteryPromoSkipsAnEvmChainTheRelayerDoesNotServe() {
        assertFalse(promo(TestAssets.usdtEth, relayed = emptySet()))
    }

    @Test
    fun theBatteryPromoWaitsForAnEmptyIdleScreen() {
        assertFalse(promo(TestAssets.usdtTon, amountEmpty = false))
        assertFalse(promo(TestAssets.usdtTon, quoteIdle = false))
    }

    private fun assertRefreshMatchesSendCutoff(lifetimeMillis: Long?, expected: Duration) {
        val refresh = quoteLifetime(arrivalLifetimeMillis = lifetimeMillis)
        assertEquals(expected, refresh)
        val receivedAt = 1_000_000L
        val refreshAt = receivedAt + refresh.inWholeMilliseconds
        assertFalse(isRouteStale(elapsedNowMillis = refreshAt - 1, receivedAt = receivedAt, lifetimeMillis = lifetimeMillis, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = refreshAt, receivedAt = receivedAt, lifetimeMillis = lifetimeMillis, quoteAgeMillis = 0))
    }

    private fun line(
        fetching: Boolean = false,
        quoteError: SwapQuoteError? = null,
        quote: DisplayQuote? = this.quote,
        plan: SwapFeePlan? = ready,
        promo: Boolean = false,
        amountEmpty: Boolean = false,
        tappable: Boolean = true,
    ) = feeLine(fetching, quoteError, quote, plan, promo, amountEmpty, tappable)

    private fun promo(
        asset: AssetEntity,
        shownBefore: Boolean = false,
        charges: Int = 0,
        relayed: Set<String> = setOfNotNull(GaslessRelayer.chainId(TestAssets.eth.value.chain)),
        amountEmpty: Boolean = true,
        quoteIdle: Boolean = true,
    ) = showsBatteryPromo(
        shownBefore = shownBefore,
        battery = SwapBattery(charges = charges, relayedEvmChains = relayed),
        sellAsset = asset.value,
        amountEmpty = amountEmpty,
        quoteIdle = quoteIdle,
    )
}

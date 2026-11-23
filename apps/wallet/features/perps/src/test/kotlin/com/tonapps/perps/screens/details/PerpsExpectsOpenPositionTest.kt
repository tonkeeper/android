package com.tonapps.perps.screens.details

import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsPortfolio
import com.tonapps.perps.data.PerpsPortfolioSnapshot
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsPositionSide
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsExpectsOpenPositionTest {

    @Test
    fun `a loading market expects the position the wallet portfolio holds on it`() {
        val snapshot = snapshot(walletId = WALLET_ID, positionMarkets = listOf(OTHER_MARKET_INDEX, MARKET_INDEX))

        assertTrue(PerpsPositionSection.Loading.expectsOpenPosition(snapshot, WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a loading market expects no position the portfolio does not hold`() {
        val snapshot = snapshot(walletId = WALLET_ID, positionMarkets = listOf(OTHER_MARKET_INDEX))

        assertFalse(PerpsPositionSection.Loading.expectsOpenPosition(snapshot, WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a loading market ignores the portfolio of another wallet`() {
        val snapshot = snapshot(walletId = OTHER_WALLET_ID, positionMarkets = listOf(MARKET_INDEX))

        assertFalse(PerpsPositionSection.Loading.expectsOpenPosition(snapshot, WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a loading market without a portfolio expects no position`() {
        assertFalse(PerpsPositionSection.Loading.expectsOpenPosition(null, WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a loaded position section wins over the portfolio`() {
        val withPosition = snapshot(walletId = WALLET_ID, positionMarkets = listOf(MARKET_INDEX))
        val withoutPosition = snapshot(walletId = WALLET_ID, positionMarkets = emptyList())

        assertTrue(PerpsPositionSection.Open(mockk()).expectsOpenPosition(withoutPosition, WALLET_ID, MARKET_INDEX))
        assertFalse(PerpsPositionSection.Hidden.expectsOpenPosition(withPosition, WALLET_ID, MARKET_INDEX))
        assertFalse(
            PerpsPositionSection.Failed(PerpsError.Unknown).expectsOpenPosition(withPosition, WALLET_ID, MARKET_INDEX),
        )
    }

    private fun snapshot(walletId: String, positionMarkets: List<Int>) = PerpsPortfolioSnapshot(
        walletId = walletId,
        loading = false,
        result = KResult.Ok(
            PerpsPortfolio(
                balance = null,
                positions = positionMarkets.map(::position),
                openOrders = null,
            ),
        ),
        lastGood = null,
    )

    private fun position(marketIndex: Int) = PerpsPosition(
        marketIndex = marketIndex,
        symbol = "BTC",
        iconUrl = null,
        side = PerpsPositionSide.LONG,
        margin = null,
        unrealizedPnl = null,
        equity = null,
        leverage = null,
        liquidationDistancePct = null,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val OTHER_WALLET_ID = "other"
        const val MARKET_INDEX = 1
        const val OTHER_MARKET_INDEX = 0
    }
}

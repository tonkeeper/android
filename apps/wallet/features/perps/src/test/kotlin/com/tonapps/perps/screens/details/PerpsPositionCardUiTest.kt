package com.tonapps.perps.screens.details

import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseLeg
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsPositionCardUiTest {

    @Test
    fun `order rows show the profit and the loss on the margin at any leverage`() {
        listOf(
            Triple(BigDecimal("1"), "120", "80"),
            Triple(BigDecimal("2"), "110", "90"),
            Triple(BigDecimal("10"), "102", "98"),
        ).forEach { (size, takeProfit, stopLoss) ->
            val card = detail(
                side = PerpsPositionSide.LONG,
                size = size,
                takeProfit = BigDecimal(takeProfit),
                stopLoss = BigDecimal(stopLoss),
            ).toCard()

            assertEquals("+$20", card.takeProfit?.projectedPnlFormat)
            assertEquals("−$20", card.stopLoss?.projectedPnlFormat)
        }
    }

    @Test
    fun `short order rows gain below the entry and lose above it`() {
        val card = detail(
            side = PerpsPositionSide.SHORT,
            size = BigDecimal("10"),
            takeProfit = BigDecimal("98"),
            stopLoss = BigDecimal("102"),
        ).toCard()

        assertEquals("+$20", card.takeProfit?.projectedPnlFormat)
        assertEquals("−$20", card.stopLoss?.projectedPnlFormat)
    }

    @Test
    fun `order row takes the percent the backend projected`() {
        val card = detail(
            side = PerpsPositionSide.LONG,
            size = BigDecimal("10"),
            takeProfit = BigDecimal("102"),
            stopLoss = null,
            leverage = 10,
            takeProfitRoi = BigDecimal("19.57"),
        ).toCard()

        assertEquals(formatSignedPercent(BigDecimal("19.57")), card.takeProfit?.projectedRoiFormat)
    }

    @Test
    fun `order row set a moment ago counts its percent on the margin at entry`() {
        val card = detail(
            side = PerpsPositionSide.LONG,
            size = BigDecimal("10"),
            takeProfit = BigDecimal("102"),
            stopLoss = BigDecimal("98"),
            leverage = 10,
        ).toCard()

        assertEquals(0, BigDecimal("20").compareTo(card.takeProfit?.projectedRoi))
        assertEquals(0, BigDecimal("-20").compareTo(card.stopLoss?.projectedRoi))
    }

    @Test
    fun `order row keeps the dash when the position has no entry price`() {
        val card = detail(
            side = PerpsPositionSide.LONG,
            size = BigDecimal("10"),
            takeProfit = BigDecimal("102"),
            stopLoss = null,
            entryPrice = null,
        ).toCard()

        assertEquals(PERPS_EMPTY_VALUE, card.takeProfit?.projectedPnlFormat)
    }

    @Test
    fun `hidden balance masks the order row profit`() {
        val card = detail(
            side = PerpsPositionSide.LONG,
            size = BigDecimal("10"),
            takeProfit = BigDecimal("102"),
            stopLoss = null,
        ).toCard(hiddenBalance = true)

        assertEquals(HIDDEN_BALANCE, card.takeProfit?.projectedPnlFormat)
    }

    private fun PerpsPositionDetail.toCard(hiddenBalance: Boolean = false) = toCardUi(
        symbol = "BTC",
        sizeDecimals = 5,
        hiddenBalance = hiddenBalance,
    )

    private fun detail(
        side: PerpsPositionSide,
        size: BigDecimal,
        takeProfit: BigDecimal?,
        stopLoss: BigDecimal?,
        entryPrice: BigDecimal? = ENTRY,
        leverage: Int? = null,
        takeProfitRoi: BigDecimal? = null,
    ) = PerpsPositionDetail(
        position = PerpsOpenPosition(
            id = "lighter:1",
            marketIndex = 1,
            symbol = "BTC",
            side = side,
            size = size,
            positionValue = null,
            avgEntryPrice = entryPrice,
            markPrice = null,
            liquidationPrice = null,
            liquidationDistancePct = null,
            margin = MARGIN,
            equity = null,
            unrealizedPnl = null,
            roiPct = null,
            leverage = leverage,
            fundingPaid = null,
        ),
        autoCloseKnown = true,
        takeProfit = takeProfit?.let { leg(PerpsAutoCloseLegKind.TAKE_PROFIT, it, takeProfitRoi) },
        stopLoss = stopLoss?.let { leg(PerpsAutoCloseLegKind.STOP_LOSS, it, null) },
    )

    private fun leg(
        kind: PerpsAutoCloseLegKind,
        triggerPrice: BigDecimal,
        projectedRoiPct: BigDecimal?,
    ) = PerpsAutoCloseLeg(
        kind = kind,
        orderIndex = null,
        clientOrderIndex = null,
        triggerPrice = triggerPrice,
        sharePct = null,
        projectedRoiPct = projectedRoiPct,
    )

    private companion object {
        val ENTRY = BigDecimal("100")
        val MARGIN = BigDecimal("100")
    }
}

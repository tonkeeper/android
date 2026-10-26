package com.tonapps.perps.screens.sizechange

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsSizeDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsSizeChangeAutoCloseTest {

    @Test
    fun `add keeps the typed percent of the new leg next to the resting one`() {
        val draft = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("110"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("10"),
        )
        val resting = PerpsAutoCloseDraft(takeProfit = BigDecimal("120"), stopLoss = BigDecimal("90"))

        val merged = draft.withRestingLegs(PerpsSizeDirection.ADD, resting)

        assertEquals(
            PerpsAutoCloseDraft(
                takeProfit = BigDecimal("110"),
                stopLoss = BigDecimal("90"),
                takeProfitRoi = BigDecimal("10"),
            ),
            merged,
        )
    }
}

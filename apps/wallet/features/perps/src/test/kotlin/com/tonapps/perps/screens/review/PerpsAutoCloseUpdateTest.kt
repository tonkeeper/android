package com.tonapps.perps.screens.review

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.wallet.localization.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseUpdateTest {

    private val draft = PerpsAutoCloseDraft(
        takeProfit = BigDecimal("80000"),
        stopLoss = BigDecimal("76000"),
        takeProfitRoi = BigDecimal("20"),
        stopLossRoi = BigDecimal("-10"),
    )

    @Test
    fun `continuing without the stale stop loss keeps the take profit with its roi`() {
        val remaining = draft.without(setOf(PerpsAutoCloseField.STOP_LOSS))

        assertEquals(
            PerpsAutoCloseDraft(takeProfit = BigDecimal("80000"), stopLoss = null, takeProfitRoi = BigDecimal("20")),
            remaining,
        )
    }

    @Test
    fun `continuing without both stale legs leaves no auto close`() {
        assertNull(draft.without(setOf(PerpsAutoCloseField.TAKE_PROFIT, PerpsAutoCloseField.STOP_LOSS)))
    }

    @Test
    fun `dropping the only leg leaves no auto close`() {
        val stopLossOnly = PerpsAutoCloseDraft(takeProfit = null, stopLoss = BigDecimal("76000"))

        assertNull(stopLossOnly.without(setOf(PerpsAutoCloseField.STOP_LOSS)))
    }

    @Test
    fun `the sheet offers to continue only when the refused legs came from the review`() {
        val fix = PerpsAutoCloseFix(field = PerpsAutoCloseField.STOP_LOSS, suggestion = null, draft = draft)
        val withDraft = PerpsReviewSheet.AutoCloseUpdate(fix = fix, stale = setOf(PerpsAutoCloseField.STOP_LOSS))
        val withoutDraft = withDraft.copy(fix = fix.copy(draft = null))

        assertTrue(withDraft.canContinue)
        assertEquals(draft.without(setOf(PerpsAutoCloseField.STOP_LOSS)), withDraft.remaining)
        assertFalse(withoutDraft.canContinue)
    }

    @Test
    fun `the title names the leg or the whole auto close`() {
        assertEquals(
            Localization.perps_review_tp_update_title,
            setOf(PerpsAutoCloseField.TAKE_PROFIT).autoCloseUpdateTitle(),
        )
        assertEquals(
            Localization.perps_review_sl_update_title,
            setOf(PerpsAutoCloseField.STOP_LOSS).autoCloseUpdateTitle(),
        )
        assertEquals(
            Localization.perps_review_auto_close_update_title,
            setOf(PerpsAutoCloseField.TAKE_PROFIT, PerpsAutoCloseField.STOP_LOSS).autoCloseUpdateTitle(),
        )
        assertEquals(Localization.perps_review_auto_close_invalid, emptySet<PerpsAutoCloseField>().autoCloseUpdateTitle())
    }
}

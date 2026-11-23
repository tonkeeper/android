package com.tonapps.ledgerkit.transport.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanStartBudgetTest {

    private var now = 1_000L
    private val budget = ScanStartBudget(maxStarts = 4, windowMs = 30_000L, clock = { now })

    @Test
    fun `background scans leave a start free for the user`() {
        repeat(3) { assertEquals(0L, budget.reserve(keepFree = 1)) }

        assertEquals(30_000L, budget.reserve(keepFree = 1))
        assertEquals(0L, budget.reserve())
    }

    @Test
    fun `a held back background scan waits for the start that frees its slot`() {
        budget.reserve(keepFree = 1)
        now += 5_000L
        budget.reserve(keepFree = 1)
        now += 5_000L
        budget.reserve(keepFree = 1)
        now += 5_000L

        assertEquals(15_000L, budget.reserve(keepFree = 1))

        now += 15_000L
        assertEquals(0L, budget.reserve(keepFree = 1))
    }

    @Test
    fun `user scans use the whole window`() {
        repeat(4) { assertEquals(0L, budget.reserve()) }

        assertEquals(30_000L, budget.reserve())
    }
}

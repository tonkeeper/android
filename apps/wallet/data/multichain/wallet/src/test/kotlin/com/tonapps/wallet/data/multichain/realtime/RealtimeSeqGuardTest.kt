package com.tonapps.wallet.data.multichain.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSeqGuardTest {

    private val guard = RealtimeSeqGuard()

    @Test
    fun `applies the first and newer seq`() {
        assertTrue(guard.markApplied("w1", "balance.hint", 5))
        assertTrue(guard.markApplied("w1", "balance.hint", 6))
    }

    @Test
    fun `drops equal and older seq for the same key`() {
        guard.markApplied("w1", "balance.hint", 5)

        assertFalse(guard.markApplied("w1", "balance.hint", 5))
        assertFalse(guard.markApplied("w1", "balance.hint", 4))
    }

    @Test
    fun `tracks events and wallets independently`() {
        guard.markApplied("w1", "balance.hint", 5)

        assertTrue(guard.markApplied("w1", "activity.hint", 1))
        assertTrue(guard.markApplied("w2", "balance.hint", 1))
    }
}

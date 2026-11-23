package com.tonapps.core.deeplink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DAppDeepLinkGateTest {

    private val gate = DAppDeepLinkGate(repeatWindowMs = 2_000L)

    @Test
    fun `rejects the same link while it is opening`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))

        assertFalse(gate.tryEnter(TRUMP, now = 100L))
    }

    @Test
    fun `accepts another link while one is opening`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))

        assertTrue(gate.tryEnter(OTHER, now = 100L))
    }

    @Test
    fun `finishing one link keeps another one opening`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        assertTrue(gate.tryEnter(OTHER, now = 100L))
        gate.exit(TRUMP, now = 200L, isScreenOpenOrPending = { false })

        assertFalse(gate.tryEnter(OTHER, now = 300L))
    }

    @Test
    fun `rejects the same link while its screen is still open`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        gate.exit(TRUMP, now = 3_000L, isScreenOpenOrPending = { true })

        assertFalse(gate.tryEnter(TRUMP, now = 4_999L))
    }

    @Test
    fun `accepts the same link once its screen closed`() {
        var screenOpen = true
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        gate.exit(TRUMP, now = 100L, isScreenOpenOrPending = { screenOpen })

        screenOpen = false

        assertTrue(gate.tryEnter(TRUMP, now = 200L))
    }

    @Test
    fun `accepts the same link once the repeat window passed`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        gate.exit(TRUMP, now = 3_000L, isScreenOpenOrPending = { true })

        assertTrue(gate.tryEnter(TRUMP, now = 5_000L))
    }

    @Test
    fun `accepts another link right after one opened`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        gate.exit(TRUMP, now = 100L, isScreenOpenOrPending = { true })

        assertTrue(gate.tryEnter(OTHER, now = 200L))
    }

    @Test
    fun `accepts the same link again when nothing was opened`() {
        assertTrue(gate.tryEnter(TRUMP, now = 0L))
        gate.exit(TRUMP, now = 100L, isScreenOpenOrPending = { false })

        assertTrue(gate.tryEnter(TRUMP, now = 200L))
    }

    private companion object {
        const val TRUMP = "https://trump.keeperwallet.com/"
        const val OTHER = "https://app.ston.fi/"
    }
}

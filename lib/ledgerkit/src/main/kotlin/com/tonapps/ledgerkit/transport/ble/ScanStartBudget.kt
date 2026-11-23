package com.tonapps.ledgerkit.transport.ble

internal class ScanStartBudget(
    private val maxStarts: Int,
    private val windowMs: Long,
    private val clock: () -> Long,
) {

    private val starts = ArrayDeque<Long>()

    fun reserve(keepFree: Int = 0): Long = synchronized(starts) {
        val now = clock()
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) {
            starts.removeFirst()
        }
        val limit = maxStarts - keepFree
        if (starts.size >= limit) {
            windowMs - (now - starts[starts.size - limit])
        } else {
            starts.addLast(now)
            0L
        }
    }
}

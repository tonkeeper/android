package com.tonapps.wallet.testing

import com.tonapps.time.TimeProvider

/**
 * Server time [offsetMs] away from the device clock: a test sets it to show that what it checks
 * reads the server's clock, not the device's.
 */
class FakeTimeProvider(var offsetMs: Long = 0L) : TimeProvider {

    override fun currentTimeMillis(): Long = System.currentTimeMillis() + offsetMs
}

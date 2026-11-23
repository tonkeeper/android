package com.tonapps.deposit.multicoin.moonpay.api

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

// Unlike TimeSource.Monotonic (System.nanoTime), keeps counting while the device sleeps,
// so a quote does not stay fresh across a locked screen.
internal object ElapsedRealtimeTimeSource : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtimeNanos()
}

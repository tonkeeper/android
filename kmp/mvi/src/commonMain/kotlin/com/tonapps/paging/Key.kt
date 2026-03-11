package com.tonapps.paging

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object Key {
    @OptIn(ExperimentalTime::class)
    fun unique(): Long {
        return Clock.System.now().nanosecondsOfSecond.toLong()
    }
}

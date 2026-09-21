package com.tonapps.wallet.data.raffle

import java.time.OffsetDateTime

object RaffleClock {

    @Volatile
    var debugNow: OffsetDateTime? = null
        internal set

    fun now(): OffsetDateTime = debugNow ?: OffsetDateTime.now()
}

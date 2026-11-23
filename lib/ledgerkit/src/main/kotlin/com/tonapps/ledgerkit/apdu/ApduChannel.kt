package com.tonapps.ledgerkit.apdu

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun interface ApduChannel {

    suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse

    companion object {
        val NON_INTERACTIVE_TIMEOUT = 10.seconds
    }
}

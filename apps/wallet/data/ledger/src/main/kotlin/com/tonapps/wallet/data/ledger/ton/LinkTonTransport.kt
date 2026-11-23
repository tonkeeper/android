package com.tonapps.wallet.data.ledger.ton

import com.tonapps.ledger.transport.Transport
import com.tonapps.ledgerkit.apdu.ApduChannel
import kotlin.time.Duration

internal class LinkTonTransport(private val channel: ApduChannel) : Transport {

    override suspend fun exchange(apdu: ByteArray): ByteArray = channel.exchange(apdu, timeout = timeoutFor(apdu)).toRaw()

    private fun timeoutFor(apdu: ByteArray): Duration? {
        if (apdu.size < HEADER_LENGTH) {
            return null
        }
        val ins = apdu[1].toInt() and 0xff
        val p1 = apdu[2].toInt() and 0xff
        val p2 = apdu[3].toInt() and 0xff
        val isInteractive = when (ins) {
            INS_VERSION -> false
            INS_ADDRESS -> p1 != 0
            INS_SIGN_TX, INS_SIGN_DATA -> p2 and P2_MORE == 0
            else -> true
        }
        return if (isInteractive) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT }
    }

    private companion object {
        const val HEADER_LENGTH = 4
        const val INS_VERSION = 0x03
        const val INS_ADDRESS = 0x05
        const val INS_SIGN_TX = 0x06
        const val INS_SIGN_DATA = 0x09
        const val P2_MORE = 0x02
    }
}

package com.tonapps.blockchain.ton.extensions

import io.ktor.utils.io.core.ByteReadPacket
import org.ton.crypto.base64
import org.ton.crypto.base64url

fun String.byteReadPacket(): ByteReadPacket {
    val packet = ByteReadPacket(
        try {
            base64url(this)
        } catch (e: Exception) {
            try {
                base64(this)
            } catch (e: Exception) {
                throw IllegalArgumentException("Can't parse address: $this", e)
            }
        }
    )
    if (packet.remaining == 36L) {
        return packet
    } else {
        throw IllegalArgumentException("Invalid address length: ${packet.remaining}")
    }
}

package com.tonapps.ledgerkit.transport

import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduResponse
import kotlin.time.Duration

interface LedgerTransport {

    suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit = {}): LedgerLink
}

interface LedgerLink {

    val deviceId: String

    val model: LedgerModel

    val isConnected: Boolean

    val kind: LedgerLinkKind
        get() = LedgerLinkKind.Bluetooth

    suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse

    suspend fun awaitDisconnected()

    fun close()
}

enum class LedgerLinkKind {
    Bluetooth,
    Usb,
}

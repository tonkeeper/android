package com.tonapps.wallet.data.ledger

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.session.LedgerSession
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlin.time.Duration

class FakeLedgerTransport(
    private val appName: String,
    private val appVersion: String = "9.9.9",
    private val handler: (ByteArray) -> ApduResponse,
) : LedgerTransport {

    override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink = object : LedgerLink {
        override val deviceId: String = deviceId
        override val model: LedgerModel = LedgerModel.Stax
        override val isConnected: Boolean = true

        override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse {
            val cla = apdu[0].toInt() and 0xff
            val ins = apdu[1].toInt() and 0xff
            if (cla == 0xb0 && ins == 0x01) {
                val name = appName.toByteArray()
                val version = appVersion.toByteArray()
                return ApduResponse(byteArrayOf(1, name.size.toByte()) + name + byteArrayOf(version.size.toByte()) + version, 0x9000)
            }
            return handler(apdu)
        }

        override suspend fun awaitDisconnected() = Unit

        override fun close() = Unit
    }

    fun scope(onReport: (LedgerProgress) -> Unit = {}): LedgerScope {
        val session = LedgerSession(this, "fake")
        return object : LedgerScope {
            override val session: LedgerSession = session
            override fun report(progress: LedgerProgress) = onReport(progress)
            override suspend fun ensureApp(app: LedgerAppId): LedgerAppInfo = session.ensureApp(app)
        }
    }
}

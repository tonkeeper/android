package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration

class FakeLedgerDevice(
    var currentApp: String = "BOLOS",
    var lockedPolls: Int = 0,
    var installedApps: Set<String> = setOf("TON", "Ethereum", "Tron", "Bitcoin"),
    var refuseOpen: Boolean = false,
    var disconnectsOnAppSwitch: Boolean = true,
    var dropsBeforeOpenResponse: Boolean = false,
) : LedgerTransport {

    val sent = mutableListOf<ByteArray>()
    var connectCount = 0
    var appHandler: (ByteArray) -> ApduResponse = { ApduResponse(ByteArray(0), 0x6d00) }
    private var activeLink: FakeLink? = null

    override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
        connectCount++
        return FakeLink(deviceId).also { activeLink = it }
    }

    inner class FakeLink(override val deviceId: String) : LedgerLink {
        private val disconnected = CompletableDeferred<Unit>()
        override val model: LedgerModel = LedgerModel.Stax
        override val isConnected: Boolean
            get() = !disconnected.isCompleted

        override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse {
            if (!isConnected) {
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
            sent += apdu
            val cla = apdu[0].toInt() and 0xff
            val ins = apdu[1].toInt() and 0xff
            return when {
                cla == 0xb0 && ins == 0x01 -> appInfo()
                cla == 0xb0 && ins == 0xa7 -> {
                    currentApp = "BOLOS"
                    switchApp()
                    ok()
                }
                cla == 0xe0 && ins == 0xd8 -> openApp(String(apdu, 5, apdu[4].toInt(), Charsets.US_ASCII))
                else -> appHandler(apdu)
            }
        }

        private fun appInfo(): ApduResponse {
            if (lockedPolls > 0) {
                lockedPolls--
                return ApduResponse(ByteArray(0), 0x5515)
            }
            val name = currentApp.toByteArray()
            val version = "1.2.3".toByteArray()
            return ApduResponse(byteArrayOf(1, name.size.toByte()) + name + byteArrayOf(version.size.toByte()) + version, 0x9000)
        }

        private fun openApp(name: String): ApduResponse {
            if (currentApp != "BOLOS") {
                return ApduResponse(ByteArray(0), 0x6d00)
            }
            if (name !in installedApps) {
                return ApduResponse(ByteArray(0), 0x6807)
            }
            if (refuseOpen) {
                return ApduResponse(ByteArray(0), 0x5501)
            }
            currentApp = name
            switchApp()
            if (dropsBeforeOpenResponse) {
                disconnected.complete(Unit)
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
            return ok()
        }

        private fun switchApp() {
            if (disconnectsOnAppSwitch) {
                disconnected.complete(Unit)
            }
        }

        override suspend fun awaitDisconnected() = disconnected.await()

        override fun close() {
            disconnected.complete(Unit)
        }
    }

    companion object {
        fun ok(data: ByteArray = ByteArray(0)) = ApduResponse(data, 0x9000)

        fun rawApdu(cla: Int, ins: Int) = Apdu.build(cla, ins, 0, 0)
    }
}

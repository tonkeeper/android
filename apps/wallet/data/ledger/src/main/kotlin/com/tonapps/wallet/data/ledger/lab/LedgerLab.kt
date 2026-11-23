package com.tonapps.wallet.data.ledger.lab

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.usb.LedgerUsbDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.time.Duration

internal class LedgerLab(private val context: Context) {

    val isDebuggable: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    val isEnabled: Boolean
        get() = isDebuggable && flagFile.exists()

    private val isUsb: Boolean
        get() = isEnabled && config().firstOrNull() == USB_MODE

    private val flagFile: File
        get() = File(context.filesDir, FLAG_FILE)

    fun devices(): List<LedgerBleDevice> = if (isEnabled && !isUsb) {
        listOf(LedgerBleDevice(DEVICE_ID, DEVICE_NAME, LedgerModel.Stax, rssi = 0, lastSeenAt = SystemClock.elapsedRealtime()))
    } else {
        emptyList()
    }

    fun usbDevices(): List<LedgerUsbDevice> = if (isUsb) {
        listOf(LedgerUsbDevice(USB_DEVICE_NAME, LedgerModel.Stax))
    } else {
        emptyList()
    }

    fun route(transport: LedgerTransport): LedgerTransport = object : LedgerTransport {
        override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink = when {
            isUsb -> connectLab(deviceId, LedgerLinkKind.Usb)
            deviceId == DEVICE_ID && isEnabled -> connectLab(deviceId, LedgerLinkKind.Bluetooth)
            else -> transport.connect(deviceId, onProgress)
        }
    }

    private suspend fun connectLab(deviceId: String, kind: LedgerLinkKind): LedgerLink = withContext(Dispatchers.IO) {
        val (host, port) = endpoint()
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            LabLink(deviceId, socket, kind)
        } catch (e: IOException) {
            throw LedgerFailureException(LedgerFailure.DeviceNotFound)
        }
    }

    private fun config(): List<String> =
        flagFile.takeIf { it.exists() }?.readText()?.trim()?.split(WHITESPACE)?.filter { it.isNotEmpty() }.orEmpty()

    private fun endpoint(): Pair<String, Int> {
        val configured = config().lastOrNull { it != USB_MODE }.orEmpty()
        val host = configured.substringBefore(':').ifBlank { DEFAULT_HOST }
        val port = configured.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
        return host to port
    }

    private class LabLink(
        override val deviceId: String,
        private val socket: Socket,
        override val kind: LedgerLinkKind,
    ) : LedgerLink {

        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private val mutex = Mutex()

        @Volatile
        private var closed = false

        override val model: LedgerModel = LedgerModel.Stax

        override val isConnected: Boolean
            get() = !closed && !socket.isClosed

        override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse = mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    socket.soTimeout = timeout?.inWholeMilliseconds?.toInt() ?: 0
                    output.writeInt(apdu.size)
                    output.write(apdu)
                    output.flush()
                    val size = input.readInt()
                    val data = ByteArray(size + STATUS_WORD_LENGTH)
                    input.readFully(data)
                    val statusWord = ((data[size].toInt() and 0xff) shl 8) or (data[size + 1].toInt() and 0xff)
                    ApduResponse(data.copyOfRange(0, size), statusWord)
                } catch (e: SocketTimeoutException) {
                    close()
                    throw LedgerFailureException(LedgerFailure.Timeout(LedgerPhase.Apdu))
                } catch (e: IOException) {
                    close()
                    throw LedgerFailureException(LedgerFailure.ConnectionLost)
                }
            }
        }

        override suspend fun awaitDisconnected() = withContext(Dispatchers.IO) {
            while (isActive && isConnected) {
                try {
                    socket.soTimeout = POLL_INTERVAL_MS
                    if (input.read() == -1) {
                        closed = true
                    }
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    closed = true
                }
            }
        }

        override fun close() {
            closed = true
            try {
                socket.close()
            } catch (e: IOException) {
                closed = true
            }
        }
    }

    private companion object {
        const val FLAG_FILE = "ledger_lab"
        const val DEVICE_ID = "LEDGER-LAB"
        const val DEVICE_NAME = "Stax (Ledger lab)"
        const val USB_DEVICE_NAME = "/dev/bus/usb/ledger-lab"
        const val USB_MODE = "usb"
        val WHITESPACE = Regex("\\s+")
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 9990
        const val CONNECT_TIMEOUT_MS = 2_000
        const val POLL_INTERVAL_MS = 200
        const val STATUS_WORD_LENGTH = 2
    }
}

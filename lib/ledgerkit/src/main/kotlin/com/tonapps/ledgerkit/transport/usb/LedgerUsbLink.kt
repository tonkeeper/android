package com.tonapps.ledgerkit.transport.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.ble.LedgerFramingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal interface LedgerUsbPort {

    fun write(packet: ByteArray): Boolean

    fun read(buffer: ByteArray, timeout: Duration): Int

    fun close()
}

internal class LedgerUsbLink(
    override val deviceId: String,
    override val model: LedgerModel,
    private val port: LedgerUsbPort,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : LedgerLink {

    private val exchangeMutex = Mutex()
    private val io = ReentrantLock()
    private val lost = CompletableDeferred<Unit>()
    private val portClosed = AtomicBoolean(false)

    @Volatile
    private var closed = false

    @Volatile
    var onClose: () -> Unit = {}

    override val kind: LedgerLinkKind
        get() = LedgerLinkKind.Usb

    override val isConnected: Boolean
        get() = !closed && !lost.isCompleted

    override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse = exchangeMutex.withLock {
        if (!isConnected) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
        withContext(ioDispatcher) {
            try {
                transmit(apdu)
                Apdu.parseResponse(receive(timeout))
            } catch (e: CancellationException) {
                close()
                throw e
            } catch (e: LedgerFramingException) {
                close()
                throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Framing error"), cause = e)
            } catch (e: IllegalArgumentException) {
                throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Malformed response"), cause = e)
            }
        }
    }

    override suspend fun awaitDisconnected() {
        lost.await()
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        lost.complete(Unit)
        onClose()
        if (io.tryLock()) {
            try {
                closePort()
            } finally {
                io.unlock()
            }
        }
    }

    fun markLost() {
        lost.complete(Unit)
    }

    private fun transmit(apdu: ByteArray) {
        for (packet in LedgerUsbFraming.split(apdu)) {
            if (!ioCall { port.write(packet) }) {
                markLost()
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
        }
    }

    private suspend fun receive(timeout: Duration?): ByteArray {
        val reassembler = LedgerUsbReassembler()
        val deadline = timeout?.let { timeSource.markNow() + it }
        val buffer = ByteArray(LedgerUsbFraming.PACKET_SIZE)
        var quickFailures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!isConnected) {
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
            if (deadline?.hasPassedNow() == true) {
                close()
                throw LedgerFailureException(LedgerFailure.Timeout(LedgerPhase.Apdu))
            }
            val started = timeSource.markNow()
            val read = ioCall { port.read(buffer, READ_SLICE) }
            if (read > 0) {
                quickFailures = 0
                reassembler.accept(buffer.copyOf(read))?.let { return it }
                continue
            }
            quickFailures = if (started.elapsedNow() < READ_SLICE / 2) { quickFailures + 1 } else { 0 }
            if (quickFailures >= MAX_QUICK_FAILURES) {
                markLost()
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
        }
    }

    private fun <T> ioCall(block: () -> T): T {
        val result = io.withLock {
            if (portClosed.get()) {
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
            block()
        }
        if (closed) {
            io.withLock { closePort() }
        }
        return result
    }

    private fun closePort() {
        if (portClosed.compareAndSet(false, true)) {
            port.close()
        }
    }

    private companion object {
        val READ_SLICE = 250.milliseconds
        const val MAX_QUICK_FAILURES = 3
    }
}

internal class LedgerUsbConnectionPort(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint,
) : LedgerUsbPort {

    override fun write(packet: ByteArray): Boolean {
        var offset = 0
        while (offset < packet.size) {
            val length = minOf(output.maxPacketSize.coerceAtLeast(1), packet.size - offset)
            val written = connection.bulkTransfer(output, packet, offset, length, WRITE_TIMEOUT_MS)
            if (written <= 0) {
                return false
            }
            offset += written
        }
        return true
    }

    override fun read(buffer: ByteArray, timeout: Duration): Int =
        connection.bulkTransfer(input, buffer, buffer.size, timeout.inWholeMilliseconds.toInt())

    override fun close() {
        try {
            connection.releaseInterface(usbInterface)
        } finally {
            connection.close()
        }
    }

    companion object {

        private const val WRITE_TIMEOUT_MS = 1_000

        fun find(usbInterface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
            val endpoints = (0 until usbInterface.endpointCount).map(usbInterface::getEndpoint)
            val input = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN } ?: return null
            val output = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT } ?: return null
            return input to output
        }
    }
}

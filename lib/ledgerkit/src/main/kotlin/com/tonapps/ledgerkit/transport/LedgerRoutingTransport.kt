package com.tonapps.ledgerkit.transport

import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class LedgerRoutingTransport(
    private val usb: LedgerTransport,
    private val bluetooth: LedgerTransport,
    private val isUsbAttached: () -> Boolean,
    private val awaitUsbAttached: suspend () -> Unit = { awaitCancellation() },
    private val usbReconnectWindow: Duration = 5.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : LedgerTransport {

    private val usbDroppedAt = ConcurrentHashMap<String, TimeMark>()

    fun routesToUsb(deviceId: String): Boolean {
        val dropped = usbDroppedAt[deviceId]
        val reconnecting = dropped != null && dropped.elapsedNow() < usbReconnectWindow
        return reconnecting || LedgerUsbIds.routesToUsb(deviceId, isUsbAttached())
    }

    override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
        if (!routesToUsb(deviceId)) {
            connectBluetoothUntilPlugged(deviceId, onProgress)?.let { return it }
        }
        val link = usb.connect(deviceId, onProgress)
        usbDroppedAt.remove(deviceId)
        return UsbTrackedLink(link)
    }

    private suspend fun connectBluetoothUntilPlugged(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink? = supervisorScope {
        var bluetoothLink: LedgerLink? = null
        val viaBluetooth = async { bluetooth.connect(deviceId, onProgress).also { bluetoothLink = it } }
        val plugged = async { awaitUsbAttached() }
        val link = select {
            viaBluetooth.onAwait { it }
            plugged.onAwait { null }
        }
        if (link != null) {
            plugged.cancel()
        } else {
            viaBluetooth.cancel()
            viaBluetooth.join()
            bluetoothLink?.close()
        }
        link
    }

    private inner class UsbTrackedLink(private val link: LedgerLink) : LedgerLink by link {

        override fun close() {
            if (!link.isConnected) {
                usbDroppedAt[link.deviceId] = timeSource.markNow()
            }
            link.close()
        }
    }
}

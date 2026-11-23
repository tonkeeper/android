package com.tonapps.ledgerkit.transport.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LedgerUsbTransport(
    private val environment: LedgerUsbEnvironment,
    private val timings: LedgerUsbTimings = LedgerUsbTimings(),
) : LedgerTransport {

    @Volatile
    private var lastDeviceName: String? = null

    override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
        val present = environment.findUsbDevice()
        val device = present ?: run {
            onProgress(LedgerProgress.WaitingUsb)
            withTimeoutOrNull(timings.attach) { environment.awaitUsbDevice() }
                ?: throw LedgerFailureException(LedgerFailure.UsbNotConnected)
        }
        val reattached = present == null || lastDeviceName.let { it != null && it != device.deviceName }
        ensurePermission(device, reattached, onProgress)
        return withContext(Dispatchers.IO) { open(deviceId, device) }.also { lastDeviceName = device.deviceName }
    }

    private suspend fun ensurePermission(device: UsbDevice, reattached: Boolean, onProgress: (LedgerProgress) -> Unit) {
        if (environment.awaitPermission(device, timings.autoGrant)) {
            return
        }
        requirePresent(device)
        onProgress(LedgerProgress.UsbAccess)
        if (reattached && environment.awaitPermission(device, timings.systemPrompt)) {
            return
        }
        requirePresent(device)
        if (!environment.requestPermission(device)) {
            requirePresent(device)
            throw LedgerFailureException(LedgerFailure.UsbAccessDenied)
        }
    }

    private fun requirePresent(device: UsbDevice) {
        if (!environment.isPresent(device.deviceName)) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
    }

    private fun open(deviceId: String, device: UsbDevice): LedgerLink {
        val usbInterface = device.apduInterface() ?: throw LedgerFailureException(LedgerFailure.UnsupportedDevice)
        val (input, output) = LedgerUsbConnectionPort.find(usbInterface)
            ?: throw LedgerFailureException(LedgerFailure.UnsupportedDevice)
        val connection = environment.open(device) ?: throw LedgerFailureException(
            if (environment.hasPermission(device)) { LedgerFailure.ConnectionLost } else { LedgerFailure.UsbAccessDenied },
        )
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
        val model = LedgerModel.fromUsbProductId(device.productId) ?: LedgerUsbIds.model(deviceId) ?: LedgerModel.NanoS
        val link = LedgerUsbLink(deviceId, model, LedgerUsbConnectionPort(connection, usbInterface, input, output))
        link.onClose = environment.onDetached(device.deviceName, link::markLost)
        if (!environment.isPresent(device.deviceName)) {
            link.close()
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
        return link
    }

    private fun UsbDevice.apduInterface(): UsbInterface? {
        val interfaces = (0 until interfaceCount).map(::getInterface)
            .filter { LedgerUsbConnectionPort.find(it) != null }
        return interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_HID && it.id == 0 }
            ?: interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_HID }
            ?: interfaces.firstOrNull()
    }
}

data class LedgerUsbTimings(
    val attach: Duration = 60.seconds,
    val autoGrant: Duration = 700.milliseconds,
    val systemPrompt: Duration = 10.seconds,
)

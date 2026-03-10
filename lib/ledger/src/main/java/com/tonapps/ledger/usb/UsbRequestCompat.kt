package com.tonapps.ledger.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest

class UsbRequestCompat(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint
): UsbRequest() {

    init {
        if (!initialize(connection, endpoint)) {
            close()
            throw Exception("Failed to initialize UsbRequest")
        }
    }

    companion object {
        const val MAX_USB_FS_BUFFER_SIZE = 16384
    }
}

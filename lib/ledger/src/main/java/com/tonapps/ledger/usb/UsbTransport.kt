package com.tonapps.ledger.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.tonapps.ledger.LedgerException
import com.tonapps.ledger.transport.Transport
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class UsbTransport(
    private val connection: UsbDeviceConnection,
    private val deviceOut: UsbEndpoint,
    private val deviceIn: UsbEndpoint,
    private val usbDevice: UsbDevice
) : Transport {
    val serialNumber: String
        get() = usbDevice.serialNumber ?: "usb"

    val productId: Int
        get() = usbDevice.productId

    private fun write(apdu: ByteArray) {
        val command = LedgerHelper.wrapCommandAPDU(DEFAULT_CHANNEL, apdu, HID_PACKET_SIZE)
        connection.writeByBulk(deviceOut, command)
    }

    private fun read(): ByteArray {
        val transferBuffer = ByteArray(HID_PACKET_SIZE)
        val responseBuffer = ByteBuffer.allocate(HID_PACKET_SIZE)
        val readRequest = UsbRequestCompat(connection, deviceIn)

        var responseData: ByteArray? = null
        val response = ByteArrayOutputStream()
        while (LedgerHelper.unwrapResponseAPDU(DEFAULT_CHANNEL, response.toByteArray(), HID_PACKET_SIZE).also {
            responseData = it
            } == null) {
            responseBuffer.clear()
            if (!readRequest.queueCompat(responseBuffer, HID_PACKET_SIZE)) {
                readRequest.close()
                throw LedgerException.USBReadException
            }
            connection.requestWait()
            responseBuffer.rewind()
            responseBuffer.get(transferBuffer, 0, HID_PACKET_SIZE)
            response.write(transferBuffer, 0, HID_PACKET_SIZE)
        }
        readRequest.close()
        return responseData ?: ByteArray(0)
    }

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        write(apdu)
        return read()
    }

    override fun close() {
        super.close()
        connection.close()
    }

    private companion object {
        private const val DEFAULT_CHANNEL = 1
        private const val HID_PACKET_SIZE = 64
    }
}
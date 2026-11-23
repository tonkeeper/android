package com.tonapps.ledgerkit.transport.usb

import com.tonapps.ledgerkit.transport.LedgerModel

object LedgerUsbIds {

    private const val PREFIX = "usb:"
    private const val GENERIC = "Ledger"

    fun deviceId(model: LedgerModel?): String = PREFIX + (model?.name ?: GENERIC)

    fun isUsb(deviceId: String): Boolean = deviceId.startsWith(PREFIX, ignoreCase = true)

    fun model(deviceId: String): LedgerModel? = if (isUsb(deviceId)) {
        LedgerModel.fromName(deviceId.substring(PREFIX.length))
    } else {
        null
    }

    fun routesToUsb(deviceId: String, isUsbAttached: Boolean): Boolean = isUsbAttached || isUsb(deviceId)
}

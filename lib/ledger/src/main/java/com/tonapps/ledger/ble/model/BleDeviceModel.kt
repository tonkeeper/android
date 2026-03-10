package com.tonapps.ledger.ble.model

import java.util.*

data class BleDeviceModel(
    val id: String,
    val name: String,
    val serviceId: String? = null,
    val rssi: Int = 0,
) {
    val timestamp: Long = Date().time
}


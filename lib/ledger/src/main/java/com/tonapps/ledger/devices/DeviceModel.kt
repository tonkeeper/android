package com.tonapps.ledger.devices

data class DeviceModel(
    val id: DeviceModelId,
    val productName: String,
    val productIdMM: Int,
    val legacyUsbProductId: Int,
    val usbOnly: Boolean,
    val memorySize: Int,
    val masks: List<Int>,
    val bluetoothSpec: List<BluetoothSpec>? = null
)
package com.tonapps.ledger.devices

data class BluetoothSpec(
    val serviceUuid: String,
    val notifyUuid: String,
    val writeUuid: String,
    val writeCmdUuid: String
)
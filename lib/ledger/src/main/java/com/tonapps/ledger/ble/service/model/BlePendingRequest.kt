package com.tonapps.ledger.ble.service.model

data class BlePendingRequest(
    val id: String,
    val apdu: ByteArray
)

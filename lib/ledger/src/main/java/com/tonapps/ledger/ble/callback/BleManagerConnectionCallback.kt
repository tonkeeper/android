package com.tonapps.ledger.ble.callback

import com.tonapps.ledger.ble.model.BleDeviceModel
import com.tonapps.ledger.ble.model.BleError

interface BleManagerConnectionCallback {
    fun onConnectionSuccess(device: BleDeviceModel)
    fun onConnectionError(error: BleError)
}

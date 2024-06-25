package com.tonapps.tonkeeper.ui.screen.ledger.steps

import com.ledger.live.ble.model.BleError
import com.tonapps.ledger.devices.DeviceModel
import com.tonapps.ledger.ton.TonTransport

sealed class ConnectionState {
    data object Idle: ConnectionState()
    data object Scanning: ConnectionState()

    data class Connected (
        val deviceId: String,
        val deviceModel: DeviceModel,
    ): ConnectionState()

    data class TonAppOpened(
        val tonTransport: TonTransport,
    ): ConnectionState()

    data class Disconnected(val error: BleError? = null): ConnectionState()
}
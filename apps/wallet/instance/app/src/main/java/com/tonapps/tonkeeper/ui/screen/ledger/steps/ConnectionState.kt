package com.tonapps.tonkeeper.ui.screen.ledger.steps

import com.ledger.live.ble.model.BleError

sealed class ConnectionState {
    data object Idle: ConnectionState()
    data object Scanning: ConnectionState()
    data object Connected: ConnectionState()
    data object TonAppOpened: ConnectionState()
    data object Signed: ConnectionState()
    data class Disconnected(val error: BleError? = null): ConnectionState()
}
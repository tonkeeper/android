package com.tonapps.network.ws

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Reconnecting : ConnectionState()
}
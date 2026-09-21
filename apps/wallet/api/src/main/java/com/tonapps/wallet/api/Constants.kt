package com.tonapps.wallet.api

internal object Constants {
    const val SWAP_API = "https://swap.tonkeeper.com"
    const val TRADING_API = "https://trading.tonkeeper.com"
    const val MULTICHAIN_API = "https://multi.tonkeeper.com"
    const val PERPS_API = "$MULTICHAIN_API/tk-perps/public-api/v1"
    const val HERMES_WS = "$MULTICHAIN_API/hermes/public-api/v1/ws"
    const val REALTIME_WS = "wss://rt.tonkeeper.com/connection/websocket"
}
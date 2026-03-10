package com.tonapps.network.ws

sealed class WSEvent {
    data class Text(val text: String): WSEvent()
    data class Closed(val code: Int, val reason: String): WSEvent()
    data class Failure(val error: Throwable): WSEvent()
    data object Opened: WSEvent()
}
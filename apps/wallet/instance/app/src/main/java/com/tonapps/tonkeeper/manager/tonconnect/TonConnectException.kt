package com.tonapps.tonkeeper.manager.tonconnect

sealed class TonConnectException(message: String): Exception(message) {

    data class UnsupportedVersion(
        val version: Int
    ): TonConnectException("Unsupported TonConnect version: $version")

    data class WrongClientId(
        val clientId: String?
    ): TonConnectException("Wrong clientId: ${if (clientId.isNullOrBlank()) "null" else clientId}")

    data class RequestParsingError(
        val data: String?
    ): TonConnectException("Invalid ConnectRequest data: ${if (data.isNullOrBlank()) "null" else data}")

    data class ReturnParsingError(
        val data: String?
    ): TonConnectException("Invalid return data: ${if (data.isNullOrBlank()) "null" else data}")
}
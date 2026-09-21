package com.tonapps.bus.core.tracer

sealed class AnalyticException(
    message: String? = null,
    cause: Throwable? = null,
) : Throwable(message, cause) {

    class Vault(message: String, cause: Throwable? = null) : AnalyticException(message, cause)

    class Passcode(message: String, cause: Throwable? = null) : AnalyticException(message, cause)

    class DeviceAuth(message: String, cause: Throwable? = null) : AnalyticException(message, cause)

    class WalletAuth(message: String, cause: Throwable? = null) : AnalyticException(message, cause)

    class WalletConnect(message: String, cause: Throwable? = null) : AnalyticException(message, cause)
}

package com.tonapps.deposit.multicoin.moonpay.frame

sealed class MoonPayFrameException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class InvalidUrl(frame: String) : MoonPayFrameException("MoonPay frame $frame: URL could not be built")

    class HostGone : MoonPayFrameException("MoonPay frame host left the screen")

    class LoadFailed(reason: String) : MoonPayFrameException("MoonPay frame failed to load: $reason")

    class Malformed(reason: String, cause: Throwable? = null) :
        MoonPayFrameException("Malformed MoonPay frame message: $reason", cause)
}

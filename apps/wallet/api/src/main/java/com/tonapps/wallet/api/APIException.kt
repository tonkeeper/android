package com.tonapps.wallet.api

sealed class APIException(message: String?, cause: Throwable?): Throwable(message, cause) {

    class Emulation(boc: String, cause: Throwable? = null): APIException(
        message = "Boc: $boc",
        cause = cause
    )
}
package com.tonapps.tonkeeper.manager.tonconnect.bridge.model

import com.tonapps.tonkeeper.manager.tonconnect.exceptions.ManifestException

enum class BridgeError(val code: Int, val message: String) {
    UNKNOWN(0, "Unknown error"),
    BAD_REQUEST(1, "Bad request"),
    APP_MANIFEST_NOT_FOUND(2, "App manifest not found"),
    APP_MANIFEST_CONTENT_ERROR(3, "App manifest content error"),
    UNKNOWN_APP(100, "Unknown app"),
    USER_DECLINED_TRANSACTION(300, "User declined the transaction"),
    METHOD_NOT_SUPPORTED(400, "Method not supported");

    class Exception(val error: BridgeError): Throwable(error.message)

    companion object {

        fun BridgeError.asException(): Exception {
            return Exception(this)
        }

        fun resolve(throwable: Throwable): BridgeError {
            return when (throwable) {
                is ManifestException.NotFound -> APP_MANIFEST_NOT_FOUND
                is ManifestException.FailedParse -> APP_MANIFEST_CONTENT_ERROR
                else -> UNKNOWN
            }
        }
    }
}

package com.tonapps.tonkeeper.manager.tonconnect.bridge.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class BridgeError(val code: Int, val message: String): Parcelable {

    companion object {
        const val UNKNOWN = 0 // "Unknown error"
        const val BAD_REQUEST = 1 // "Bad request"
        const val APP_MANIFEST_NOT_FOUND = 2 // "App manifest not found"
        const val APP_MANIFEST_CONTENT_ERROR = 3 // "App manifest content error"
        const val UNKNOWN_APP = 100 // "Unknown app"
        const val USER_DECLINED_TRANSACTION = 300 // "User declined the transaction"
        const val METHOD_NOT_SUPPORTED = 400 // "Method not supported"

        fun methodNotSupported(message: String): BridgeError {
            return BridgeError(METHOD_NOT_SUPPORTED, message)
        }

        fun badRequest(message: String): BridgeError {
            return BridgeError(BAD_REQUEST, message)
        }

        fun userDeclinedTransaction(message: String = "User declined the transaction"): BridgeError {
            return BridgeError(USER_DECLINED_TRANSACTION, message)
        }

        fun unknown(message: String = "Unknown error"): BridgeError {
            return BridgeError(UNKNOWN, message)
        }

        fun unknownApp(message: String = "Unknown app"): BridgeError {
            return BridgeError(UNKNOWN_APP, message)
        }

        fun appManifestNotFound(message: String = "App manifest not found"): BridgeError {
            return BridgeError(APP_MANIFEST_NOT_FOUND, message)
        }

        fun appManifestContentError(message: String = "App manifest content error"): BridgeError {
            return BridgeError(APP_MANIFEST_CONTENT_ERROR, message)
        }
    }
}

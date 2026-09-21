package com.tonapps.wc.models

enum class WcError(val code: Int, val message: String) {
    UNKNOWN(5000, "Unknown error with request"),
    UNSUPPORTED_CHAIN(5100, "Requested chains are not supported"),
    UNSUPPORTED_METHODS(5101, "Requested methods are not supported"),

    UNSUPPORTED_REQUEST_METHOD(5201, "Unknown method(s) requested"),
    UNSUPPORTED_REQUEST_CHAIN(5203, "Scope/chain mismatch"),
    UNSUPPORTED_REQUEST_INVALID(5300, "Invalid Session Properties requested"),

    ACCOUNT_UNINITIALIZED(5303, "Activate smart wallet account before sign."),
}

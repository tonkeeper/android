package com.tonapps.tonkeeper.manager.tonconnect.bridge.model

enum class BridgeMethod(val title: String) {
    SEND_TRANSACTION("sendTransaction"),
    SIGN_DATA("signData"),
    DISCONNECT("disconnect"),
    UNKNOWN("unknown");

    companion object {
        fun of(title: String): BridgeMethod {
            return entries.firstOrNull { it.title == title } ?: UNKNOWN
        }
    }
}

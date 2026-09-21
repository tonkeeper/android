package com.tonapps.wc.models

enum class WcConnection(val value: String) {
    Deeplink("deeplink"),
    DApp("dapp"),
    Qr("qr");

    val isDeeplink get() = this == Deeplink
    val isDapp get() = this == DApp
}

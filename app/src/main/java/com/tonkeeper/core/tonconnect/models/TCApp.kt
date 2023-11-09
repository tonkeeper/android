package com.tonkeeper.core.tonconnect.models

import android.net.Uri

data class TCApp(
    val url: String,
    val clientId: String,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {

    val host: String
        get() {
            return Uri.parse(url).host ?: throw Exception("Invalid url: $url")
        }
}
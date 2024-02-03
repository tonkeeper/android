package com.tonapps.signer.extensions

import io.ktor.util.decodeBase64Bytes

fun String.fixedBase64(): String {
    return this.trim().replace(" ", "+")
}

fun String.base64(): ByteArray {
    return fixedBase64().decodeBase64Bytes()
}
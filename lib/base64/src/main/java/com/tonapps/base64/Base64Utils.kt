package com.tonapps.base64

fun String.decodeBase64(): ByteArray {
    //force non-url safe and add padding so that it can be applied to all b64 formats
    val replaced = trim()
        .replace('-', '+')
        .replace('_', '/')
        .replace("%3d", "=")
    return replaced.base64DecodedBytes
}

fun ByteArray.encodeBase64(): String {
    return base64Encoded
}

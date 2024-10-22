package com.tonapps.base64

fun String.decodeBase64(): ByteArray {
    //force non-url safe and add padding so that it can be applied to all b64 formats
    val replaced = trim()
        .replace('-', '+')
        .replace('_', '/')
        .replace("%3d", "=")
        .replace('~', '=')

    val paddedLength = (4 - replaced.length % 4) % 4
    val paddedString = replaced + "=".repeat(paddedLength)
    return paddedString.base64DecodedBytes
}

fun ByteArray.encodeBase64(): String {
    return base64Encoded
}

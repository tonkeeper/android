package com.tonapps.base64

fun String.fixBase64(): String {
    val replaced = trim()
        .replace('-', '+')
        .replace('_', '/')
    val paddedLength = (4 - replaced.length % 4) % 4
    return replaced + "=".repeat(paddedLength)
}

fun String.decodeBase64(): ByteArray {
    // force non-url safe base64
    val replaced = replace('-', '+').replace('_', '/')
    /*
    val replaced = trim()
        .replace('-', '+')
        .replace('_', '/')
    val paddedLength = (4 - replaced.length % 4) % 4
    val paddedString = replaced + "=".repeat(paddedLength)
    return paddedString.base64DecodedBytes*/
    return replaced.base64DecodedBytes
}

fun ByteArray.encodeBase64(): String {
    return base64Encoded
}

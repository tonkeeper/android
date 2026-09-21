package com.tonapps.blockchain.ton.extensions

import org.ton.kotlin.crypto.HMac
import org.ton.kotlin.crypto.Pbkdf2
import org.ton.kotlin.crypto.Sha512

fun hmac_sha512(key: String, data: String): ByteArray {
    return hmac_sha512(key.toByteArray(), data.toByteArray())
}

fun hmac_sha512(key: ByteArray, data: ByteArray): ByteArray {
    val hMac = HMac(Sha512(), key)
    hMac.update(data, 0, data.size)
    return hMac.digest()
}

fun pbkdf2_sha512(key: ByteArray, salt: ByteArray, iterations: Int, keySize: Int): ByteArray {
    val pbdkf2Sha512 = Pbkdf2(
        digest = Sha512(),
        password = key,
        salt = salt,
        iterationCount = iterations
    )

    return pbdkf2Sha512.deriveKey(keySize)
}
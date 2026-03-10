package com.tonapps.blockchain.ton.extensions

import org.ton.crypto.digest.sha2.SHA512Digest
import org.ton.crypto.kdf.PKCSS2ParametersGenerator
import org.ton.crypto.mac.hmac.HMac


fun hmac_sha512(key: String, data: String): ByteArray {
    return hmac_sha512(key.toByteArray(), data.toByteArray())
}

fun hmac_sha512(key: ByteArray, data: ByteArray): ByteArray {
    val hMac = HMac(SHA512Digest())
    hMac.init(key)
    hMac.update(data, 0, data.size)
    return hMac.build()
}

fun pbkdf2_sha512(key: ByteArray, salt: ByteArray, iterations: Int, keySize: Int): ByteArray {
    val pbdkf2Sha512 = PKCSS2ParametersGenerator(
        digest = SHA512Digest(),
        password = key,
        salt = salt,
        iterationCount = iterations
    )
    return pbdkf2Sha512.generateDerivedParameters(keySize)
}
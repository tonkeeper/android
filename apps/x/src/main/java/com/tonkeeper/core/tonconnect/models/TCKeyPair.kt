package com.tonkeeper.core.tonconnect.models

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

data class TCKeyPair(
    val privateKey: PrivateKeyEd25519
) {

    val publicKey: PublicKeyEd25519 by lazy {
        privateKey.publicKey()
    }

    constructor(privateKey: ByteArray) : this(
        privateKey = PrivateKeyEd25519(privateKey)
    )

    fun sing(message: ByteArray): ByteArray {
        return privateKey.sign(message)
    }
}
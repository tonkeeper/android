package com.tonkeeper.core.tonconnect.models

import com.google.crypto.tink.subtle.Hex
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519

data class TCKeyPair(
    val publicKey: PublicKeyEd25519,
    val privateKey: PrivateKeyEd25519
) {

    val sessionId: String by lazy {
        Hex.encode(publicKey.toByteArray())
    }

    constructor(
        publicKey: ByteArray,
        privateKey: ByteArray
    ) : this(
        publicKey = PublicKeyEd25519(publicKey),
        privateKey = PrivateKeyEd25519(privateKey)
    )

    fun sing(message: ByteArray): ByteArray {
        return privateKey.sign(message)
    }
}
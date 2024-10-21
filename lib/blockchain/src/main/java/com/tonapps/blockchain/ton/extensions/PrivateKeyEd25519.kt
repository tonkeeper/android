package com.tonapps.blockchain.ton.extensions

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.crypto.Ed25519

object EmptyPrivateKeyEd25519 {

    operator fun invoke(): PrivateKeyEd25519 {
        return PrivateKeyEd25519(ByteArray(Ed25519.KEY_SIZE_BYTES))
    }

    fun publicKey(): PublicKeyEd25519 {
        return PublicKeyEd25519(ByteArray(Ed25519.KEY_SIZE_BYTES))
    }

    fun sign(data: ByteArray): ByteArray {
        return invoke().sign(data)
    }

    fun sign(data: BitString): ByteArray {
        return invoke().sign(data)
    }

    fun PrivateKeyEd25519.sign(message: BitString): ByteArray {
        return sign(message.toByteArray())
    }
}
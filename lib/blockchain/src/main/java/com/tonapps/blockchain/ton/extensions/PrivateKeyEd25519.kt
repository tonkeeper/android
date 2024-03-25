package com.tonapps.blockchain.ton.extensions

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.Bits256
import org.ton.crypto.Ed25519

object EmptyPrivateKeyEd25519 : PrivateKeyEd25519 {

    override val key: Bits256 = Bits256(ByteArray(Ed25519.KEY_SIZE_BYTES))

    override fun decrypt(data: ByteArray): ByteArray {
        return ByteArray(Ed25519.KEY_SIZE_BYTES * 2)
    }

    override fun publicKey(): PublicKeyEd25519 {
        return PublicKeyEd25519(key)
    }

    override fun sharedKey(publicKey: PublicKeyEd25519): ByteArray {
        return ByteArray(Ed25519.KEY_SIZE_BYTES * 2)
    }

    override fun sign(message: ByteArray): ByteArray {
        return ByteArray(Ed25519.KEY_SIZE_BYTES * 2)
    }

}
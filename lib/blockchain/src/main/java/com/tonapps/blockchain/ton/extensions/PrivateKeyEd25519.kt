package com.tonapps.blockchain.ton.extensions

import kotlinx.io.bytestring.ByteString
import org.ton.bitstring.BitString
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519

object EmptyPrivateKeyEd25519 {

    private const val KEY_SIZE_BYTES = 32

    operator fun invoke(): PrivateKeyEd25519 {
        return PrivateKeyEd25519(ByteArray(KEY_SIZE_BYTES))
    }

    fun publicKey(): PublicKeyEd25519 {
        return PublicKeyEd25519(ByteString(ByteArray(KEY_SIZE_BYTES)))
    }

    fun sign(data: ByteArray): ByteArray {
        return invoke().signToByteArray(data)
    }

    fun sign(data: BitString): ByteArray {
        return invoke().sign(data)
    }

    fun PrivateKeyEd25519.sign(message: BitString): ByteArray {
        return signToByteArray(message.toByteArray())
    }
}
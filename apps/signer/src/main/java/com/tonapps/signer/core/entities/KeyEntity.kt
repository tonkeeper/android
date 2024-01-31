package com.tonapps.signer.core.entities

import org.ton.api.pub.PublicKeyEd25519

data class KeyEntity(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
) {

    val hex: String
        get() = publicKey.key.hex().lowercase()
}
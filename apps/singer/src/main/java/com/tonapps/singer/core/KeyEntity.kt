package com.tonapps.singer.core

import org.ton.api.pub.PublicKeyEd25519

data class KeyEntity(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
)
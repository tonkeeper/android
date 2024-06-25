package com.tonapps.ledger.ton

import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd

data class Account(
    val address: AddrStd, val publicKey: PublicKeyEd25519
)

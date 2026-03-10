package com.tonapps.wallet.data.dapps.entities

import com.tonapps.security.CryptoBox

data class ConnectionEncryptedEntity(
    val keyPair: CryptoBox.KeyPair,
    val proofSignature: String?,
    val proofPayload: String?,
)
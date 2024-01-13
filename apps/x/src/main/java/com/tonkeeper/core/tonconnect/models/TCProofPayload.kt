package com.tonkeeper.core.tonconnect.models

data class TCProofPayload(
    val bufferToSign: ByteArray,
    val domainBuffer: ByteArray,
    val payload: String,
    val origin: String,
)
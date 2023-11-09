package com.tonkeeper.core.tonconnect.models

data class TCProofPayload(
    val timestamp: Int,
    val bufferToSign: ByteArray,
    val domainBuffer: ByteArray,
    val payload: String,
    val origin: String,
)
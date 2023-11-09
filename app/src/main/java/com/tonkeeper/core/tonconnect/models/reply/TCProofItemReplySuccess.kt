package com.tonkeeper.core.tonconnect.models.reply

import kotlinx.serialization.Serializable

@Serializable
data class TCProofItemReplySuccess(
    val name: String = "ton_proof",
    val proof: Proof
): TCReply() {

    @Serializable
    data class Domain(
        val lengthBytes: Long,
        val value: String
    )

    @Serializable
    data class Proof(
        val timestamp: Int,
        val domain: Domain,
        val payload: String,
        val signature: String
    )
}


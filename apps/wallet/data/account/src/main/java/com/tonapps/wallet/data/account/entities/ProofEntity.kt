package com.tonapps.wallet.data.account.entities

import kotlinx.serialization.Serializable

@Serializable
data class ProofEntity(
    val timestamp: Long,
    val domain: ProofDomainEntity,
    val payload: String?,
    val signature: String,
    val stateInit: String? = null
)
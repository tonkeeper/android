package com.tonapps.wallet.data.account.entities

import kotlinx.serialization.Serializable

@Serializable
data class ProofDomainEntity(
    val lengthBytes: Int,
    val value: String
) {

    constructor(value: String) : this(
        lengthBytes = value.toByteArray().size,
        value = value
    )
}
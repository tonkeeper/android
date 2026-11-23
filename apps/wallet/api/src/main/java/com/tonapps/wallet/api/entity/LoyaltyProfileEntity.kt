package com.tonapps.wallet.api.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoyaltyProfileEntity(
    @SerialName("show_section")
    val showSection: Boolean,
    @SerialName("show_banner")
    val showBanner: Boolean,
    val balances: List<Balance> = emptyList(),
) {

    @Serializable
    data class Balance(
        val asset: String,
        val available: String,
    )
}

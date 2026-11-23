package com.tonapps.wallet.api.tonstakers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TonstakersUpcomingRewardsResponse(
    @SerialName("wallet_tston")
    val walletTstonNano: Long,
    @SerialName("balance_tston")
    val balanceTstonNano: Long,
    @SerialName("rate")
    val tonPerTston: Double,
    val items: List<Round>,
) {

    @Serializable
    data class Round(
        @SerialName("cycle_end")
        val cycleEndSeconds: Long,
        @SerialName("reward_ton")
        val rewardNano: Long,
        val roi: Double,
    )
}

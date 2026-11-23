package com.tonapps.wallet.api.tonstakers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TonstakersRewardsResponse(
    @SerialName("total_reward_ton")
    val totalRewardNano: Long? = null,
    val pagination: Pagination,
    val items: List<Item>,
) {

    @Serializable
    data class Pagination(
        @SerialName("total")
        val totalCount: Int,
    )

    @Serializable
    data class Item(
        @SerialName("ts")
        val timestampSeconds: Long,
        @SerialName("round_id")
        val roundId: Long? = null,
        @SerialName("reward_ton")
        val rewardNano: Long? = null,
        @SerialName("apy")
        val apyFraction: Double? = null,
    )
}

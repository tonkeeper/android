package com.tonapps.wallet.data.staking.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.tonstakers.TonstakersRewardsResponse
import java.math.BigDecimal

data class StakingRewardsPage(
    val total: Coins?,
    val totalCount: Int,
    val fetchedCount: Int,
    val items: List<StakingRewardEntity>,
) {

    companion object {

        fun of(response: TonstakersRewardsResponse): StakingRewardsPage? {
            if (response.hasNegativeAmount()) {
                return null
            }
            val items = response.items.mapNotNull { item ->
                val rewardNano = item.rewardNano ?: return@mapNotNull null
                StakingRewardEntity(
                    id = item.roundId?.toString() ?: item.timestampSeconds.toString(),
                    timestampSeconds = item.timestampSeconds,
                    amount = Coins.of(rewardNano),
                    apyPercent = item.apyFraction?.let { BigDecimal(it.toString()).movePointRight(2) },
                )
            }
            return StakingRewardsPage(
                total = response.totalRewardNano?.let(Coins::of),
                totalCount = response.pagination.totalCount,
                fetchedCount = response.items.size,
                items = items,
            )
        }

        private fun TonstakersRewardsResponse.hasNegativeAmount(): Boolean {
            val total = totalRewardNano ?: 0
            return total < 0 || items.any { (it.rewardNano ?: 0) < 0 }
        }
    }
}

data class StakingRewardEntity(
    val id: String,
    val timestampSeconds: Long,
    val amount: Coins,
    val apyPercent: BigDecimal?,
)

package com.tonapps.wallet.data.staking.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.tonstakers.TonstakersUpcomingRewardsResponse
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow
import kotlin.math.roundToLong

data class StakingRewardsForecast(
    val upcoming: StakingUpcomingReward?,
    val current: StakingCurrentRewards?,
) {

    companion object {

        private const val ROUNDS_PER_MONTH = 40.0

        fun of(
            response: TonstakersUpcomingRewardsResponse,
            poolApyPercent: BigDecimal,
            nowSeconds: Long,
        ): StakingRewardsForecast? {
            if (response.isBroken()) {
                return null
            }
            val round = nextPayingRound(response.items, nowSeconds)
                ?: return StakingRewardsForecast(upcoming = null, current = null)
            return StakingRewardsForecast(
                upcoming = walletReward(response, round),
                current = currentRewards(response, round.roi, poolApyPercent),
            )
        }

        private fun nextPayingRound(
            rounds: List<TonstakersUpcomingRewardsResponse.Round>,
            nowSeconds: Long,
        ): TonstakersUpcomingRewardsResponse.Round? {
            val paying = rounds.filter { it.rewardNano > 0 }
            return paying.filter { it.cycleEndSeconds > nowSeconds }.minByOrNull { it.cycleEndSeconds }
                ?: paying.maxByOrNull { it.cycleEndSeconds }
        }

        private fun walletReward(
            response: TonstakersUpcomingRewardsResponse,
            round: TonstakersUpcomingRewardsResponse.Round,
        ): StakingUpcomingReward? {
            if (response.balanceTstonNano <= 0) {
                return null
            }
            val amountNano = BigInteger.valueOf(round.rewardNano)
                .multiply(BigInteger.valueOf(response.walletTstonNano))
                .divide(BigInteger.valueOf(response.balanceTstonNano))
                .toLong()
            if (amountNano <= 0) {
                return null
            }
            return StakingUpcomingReward(amount = Coins.of(amountNano), roundEndSeconds = round.cycleEndSeconds)
        }

        private fun currentRewards(
            response: TonstakersUpcomingRewardsResponse,
            roundRoi: Double,
            poolApyPercent: BigDecimal,
        ): StakingCurrentRewards? {
            val amountNano = response.balanceTstonNano * response.tonPerTston
            val monthlyNano = positiveNano(amountNano * ((1 + roundRoi).pow(ROUNDS_PER_MONTH) - 1))
            val yearlyNano = positiveNano(amountNano * poolApyPercent.toDouble() / 100)
            if (monthlyNano == null || yearlyNano == null) {
                return null
            }
            return StakingCurrentRewards(monthly = Coins.of(monthlyNano), yearly = Coins.of(yearlyNano))
        }

        private fun positiveNano(value: Double): Long? {
            if (!value.isFinite()) {
                return null
            }
            return value.roundToLong().takeIf { it > 0 }
        }

        private fun TonstakersUpcomingRewardsResponse.isBroken(): Boolean {
            return walletTstonNano < 0 || walletTstonNano > balanceTstonNano || items.any { it.rewardNano < 0 }
        }
    }
}

data class StakingUpcomingReward(
    val amount: Coins,
    val roundEndSeconds: Long,
)

data class StakingCurrentRewards(
    val monthly: Coins,
    val yearly: Coins,
)

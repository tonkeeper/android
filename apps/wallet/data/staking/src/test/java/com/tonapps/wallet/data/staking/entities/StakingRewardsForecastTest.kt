package com.tonapps.wallet.data.staking.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.tonstakers.TonstakersUpcomingRewardsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class StakingRewardsForecastTest {

    @Test
    fun nextRoundIsTheEarliestUnpaidOne() {
        val forecast = forecast(
            response(round(end = NOW + 200, reward = 3_000), round(end = NOW + 100, reward = 2_000)),
        )!!

        assertEquals(NOW + 100, forecast.upcoming!!.roundEndSeconds)
    }

    @Test
    fun roundWithoutRewardIsSkipped() {
        val forecast = forecast(
            response(round(end = NOW + 100, reward = 0), round(end = NOW + 200, reward = 2_000)),
        )!!

        assertEquals(NOW + 200, forecast.upcoming!!.roundEndSeconds)
    }

    @Test
    fun latestPastRoundIsUsedWhenNoneIsPending() {
        val forecast = forecast(
            response(round(end = NOW - 200, reward = 3_000), round(end = NOW - 100, reward = 2_000)),
        )!!

        assertEquals(NOW - 100, forecast.upcoming!!.roundEndSeconds)
    }

    @Test
    fun noPayingRoundGivesNoForecast() {
        val forecast = forecast(response(round(end = NOW + 100, reward = 0)))!!

        assertNull(forecast.upcoming)
        assertNull(forecast.current)
    }

    @Test
    fun upcomingCountsOnlyTheWalletShare() {
        val forecast = forecast(
            response(round(end = NOW + 100, reward = 1_000_000), wallet = 3 * TSTON, balance = 4 * TSTON),
        )!!

        assertEquals(Coins.of(750_000L), forecast.upcoming!!.amount)
    }

    @Test
    fun upcomingIsHiddenWithoutTstonOnTheWallet() {
        val withoutWallet = forecast(response(round(end = NOW + 100, reward = 1_000), wallet = 0, balance = TSTON))!!
        val withoutBalance = forecast(response(round(end = NOW + 100, reward = 1_000), wallet = 0, balance = 0))!!

        assertNull(withoutWallet.upcoming)
        assertNull(withoutBalance.upcoming)
        assertNull(withoutBalance.current)
    }

    @Test
    fun currentRewardsCompoundMonthlyAndApplyApyYearly() {
        val forecast = forecast(
            response(round(end = NOW + 100, reward = 1, roi = 0.001), wallet = 0, balance = 100 * TSTON, rate = 1.5),
            apyPercent = BigDecimal("12"),
        )!!

        assertEquals(Coins.of(6_118_495_808L), forecast.current!!.monthly)
        assertEquals(Coins.of(18_000_000_000L), forecast.current!!.yearly)
    }

    @Test
    fun brokenResponseRejectsTheForecast() {
        assertNull(forecast(response(round(end = NOW + 100, reward = -1))))
        assertNull(forecast(response(round(end = NOW + 100, reward = 1), wallet = -1)))
        assertNull(forecast(response(round(end = NOW + 100, reward = 1), wallet = 2 * TSTON, balance = TSTON)))
    }

    private fun forecast(
        response: TonstakersUpcomingRewardsResponse,
        apyPercent: BigDecimal = BigDecimal("10"),
    ): StakingRewardsForecast? {
        return StakingRewardsForecast.of(response, apyPercent, NOW)
    }

    private fun response(
        vararg rounds: TonstakersUpcomingRewardsResponse.Round,
        wallet: Long = TSTON,
        balance: Long = TSTON,
        rate: Double = 1.0,
    ): TonstakersUpcomingRewardsResponse {
        return TonstakersUpcomingRewardsResponse(
            walletTstonNano = wallet,
            balanceTstonNano = balance,
            tonPerTston = rate,
            items = rounds.toList(),
        )
    }

    private fun round(end: Long, reward: Long, roi: Double = 0.0002): TonstakersUpcomingRewardsResponse.Round {
        return TonstakersUpcomingRewardsResponse.Round(
            cycleEndSeconds = end,
            rewardNano = reward,
            roi = roi,
        )
    }

    private companion object {
        const val NOW = 1_762_000_000L
        const val TSTON = 1_000_000_000L
    }
}

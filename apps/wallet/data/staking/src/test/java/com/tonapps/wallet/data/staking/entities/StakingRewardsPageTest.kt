package com.tonapps.wallet.data.staking.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.tonstakers.TonstakersRewardsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class StakingRewardsPageTest {

    @Test
    fun rewardWithoutAmountIsSkipped() {
        val page = StakingRewardsPage.of(
            response(
                item(ts = 2, roundId = 11, reward = 1_250_000_000),
                item(ts = 1, roundId = 10, reward = null),
            )
        )!!

        assertEquals(listOf("11"), page.items.map { it.id })
        assertEquals(Coins.of(1_250_000_000L), page.items.single().amount)
    }

    @Test
    fun negativeAmountRejectsThePage() {
        assertNull(StakingRewardsPage.of(response(item(ts = 1, roundId = 1, reward = -1))))
        assertNull(StakingRewardsPage.of(response(total = -1)))
    }

    @Test
    fun apyFractionBecomesPercent() {
        val page = StakingRewardsPage.of(response(item(ts = 1, roundId = 1, reward = 1, apy = 0.1272)))!!

        assertEquals(BigDecimal("12.72"), page.items.single().apyPercent)
    }

    @Test
    fun rewardWithoutRoundIsKeyedByTime() {
        val page = StakingRewardsPage.of(response(item(ts = 1_761_000_000, roundId = null, reward = 1)))!!

        assertEquals("1761000000", page.items.single().id)
    }

    private fun response(
        vararg items: TonstakersRewardsResponse.Item,
        total: Long? = null,
    ) = TonstakersRewardsResponse(
        totalRewardNano = total,
        pagination = TonstakersRewardsResponse.Pagination(totalCount = items.size),
        items = items.toList(),
    )

    private fun item(
        ts: Long,
        roundId: Long?,
        reward: Long?,
        apy: Double? = null,
    ) = TonstakersRewardsResponse.Item(timestampSeconds = ts, roundId = roundId, rewardNano = reward, apyFraction = apy)
}

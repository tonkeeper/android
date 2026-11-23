package com.tonapps.tonkeeper.ui.screen.staking.rewards

import com.tonapps.tonkeeper.ui.screen.staking.rewards.StakingRewardFormat.RoundEnds
import org.junit.Assert.assertEquals
import org.junit.Test

class StakingRewardFormatTest {

    @Test
    fun `under a minute the round ends soon`() {
        assertEquals(RoundEnds.Soon, StakingRewardFormat.roundEnds(59))
        assertEquals(RoundEnds.Soon, StakingRewardFormat.roundEnds(0))
        assertEquals(RoundEnds.Soon, StakingRewardFormat.roundEnds(-30))
    }

    @Test
    fun `under an hour minus half a minute counts minutes`() {
        assertEquals(RoundEnds.InMinutes(1), StakingRewardFormat.roundEnds(60))
        assertEquals(RoundEnds.InMinutes(59), StakingRewardFormat.roundEnds(3569))
    }

    @Test
    fun `from an hour minus half a minute counts hours`() {
        assertEquals(RoundEnds.InHours(1), StakingRewardFormat.roundEnds(3570))
    }

    @Test
    fun `hours round to the nearest`() {
        assertEquals(RoundEnds.InHours(1), StakingRewardFormat.roundEnds(5399))
        assertEquals(RoundEnds.InHours(2), StakingRewardFormat.roundEnds(5400))
    }
}

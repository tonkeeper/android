package com.tonapps.wallet.data.staking

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool.Implementation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fees are the wallet's own fees of real mainnet events.
class StakingPoolTest {

    @Test
    fun aMaxWhalesStakeLeavesEnoughToRequestAndThenCollectAWithdrawal() {
        val fee = Coins.of(0.0006)
        val afterStake = StakingPool.getStakeMinRemaining(Implementation.Whales) - fee

        val requestRequired = StakingPool.getUnstakeRequired(Implementation.Whales, fee)
        assertTrue(afterStake >= requestRequired)

        // Even if the pool returns nothing of the request's 0.2 TON.
        val afterRequest = afterStake - StakingPool.getUnstakeTemporaryReserve(Implementation.Whales) - fee
        assertTrue(afterRequest >= StakingPool.getUnstakeRequired(Implementation.Whales, fee))
    }

    @Test
    fun aMaxTonstakersStakeLeavesEnoughToRequestAWithdrawal() {
        val afterStake = StakingPool.getStakeMinRemaining(Implementation.LiquidTF) - Coins.of(0.0037)

        assertTrue(afterStake >= StakingPool.getUnstakeRequired(Implementation.LiquidTF, Coins.of(0.004)))
    }

    @Test
    fun anUnknownFeeIsEstimatedNotCountedAsZero() {
        assertEquals(Coins.of(0.21), StakingPool.getUnstakeRequired(Implementation.Whales, networkFee = null))
        assertEquals(Coins.of(1.1), StakingPool.getUnstakeRequired(Implementation.TF, networkFee = null))
        assertEquals(Coins.of(1.1), StakingPool.getUnstakeRequired(Implementation.LiquidTF, networkFee = null))
    }

    @Test
    fun aKnownFeeReplacesTheEstimate() {
        assertEquals(Coins.of(0.204), StakingPool.getUnstakeRequired(Implementation.Whales, Coins.of(0.004)))
        assertEquals(Coins.of(1.004), StakingPool.getUnstakeRequired(Implementation.LiquidTF, Coins.of(0.004)))
    }
}

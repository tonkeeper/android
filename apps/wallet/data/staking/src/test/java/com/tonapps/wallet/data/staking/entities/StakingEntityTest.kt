package com.tonapps.wallet.data.staking.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class StakingEntityTest {

    private val whalesQueue = pool(WHALES_QUEUE_RAW, StakingPool.Implementation.Whales)
    private val tonstakers = pool(TONSTAKERS_RAW, StakingPool.Implementation.LiquidTF)

    private val staking = StakingEntity(
        pools = listOf(
            poolInfo(StakingPool.Implementation.Whales, whalesQueue),
            poolInfo(StakingPool.Implementation.LiquidTF, tonstakers),
        ),
        info = emptyList(),
    )

    @Test
    fun rawAddressFindsPool() {
        assertEquals(tonstakers, staking.findPoolByAddress(TONSTAKERS_RAW))
    }

    @Test
    fun bounceableAddressFindsPool() {
        assertEquals(tonstakers, staking.findPoolByAddress(TONSTAKERS_BOUNCEABLE))
    }

    @Test
    fun nonBounceableAddressFindsPool() {
        assertEquals(tonstakers, staking.findPoolByAddress(TONSTAKERS_NON_BOUNCEABLE))
    }

    @Test
    fun addressOfAnotherPoolFindsThatPool() {
        assertEquals(whalesQueue, staking.findPoolByAddress(WHALES_QUEUE_RAW))
    }

    @Test
    fun unknownAddressFindsNothing() {
        assertNull(staking.findPoolByAddress(UNKNOWN_RAW))
    }

    private fun pool(address: String, implementation: StakingPool.Implementation) = PoolEntity(
        address = address,
        name = implementation.title,
        implementation = implementation,
        minStake = Coins.ZERO,
        apy = BigDecimal.ZERO,
        verified = true,
        cycleStart = 0,
        cycleEnd = 0,
        liquidJettonMaster = null,
        maxApy = false,
    )

    private fun poolInfo(implementation: StakingPool.Implementation, pool: PoolEntity) = PoolInfoEntity(
        implementation = implementation,
        pools = listOf(pool),
        details = PoolDetailsEntity(
            name = implementation.title,
            description = "",
            url = "",
            socials = emptyList(),
        ),
    )

    companion object {
        private const val TONSTAKERS_RAW = "0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa"
        private const val TONSTAKERS_BOUNCEABLE = "EQCkWxfyhAkim3g2DjKQQg8T5P4g-Q1-K_jErGcDJZ4i-vqR"
        private const val TONSTAKERS_NON_BOUNCEABLE = "UQCkWxfyhAkim3g2DjKQQg8T5P4g-Q1-K_jErGcDJZ4i-qdU"
        private const val WHALES_QUEUE_RAW = "0:00ff9fdd8b3b80d70e8ea734d262f5e1bd4c184c33535bf3190dd67408629e7a"
        private const val UNKNOWN_RAW = "0:0000000000000000000000000000000000000000000000000000000000000000"
    }
}

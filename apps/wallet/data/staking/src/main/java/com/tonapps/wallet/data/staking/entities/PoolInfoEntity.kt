package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import io.tonapi.models.PoolImplementation
import io.tonapi.models.PoolInfo
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class PoolInfoEntity(
    val implementation: StakingPool.Implementation,
    val pools: List<PoolEntity>,
    val details: PoolDetailsEntity,
): Parcelable {

    val apy = pools.map { it.apy }.maxOrNull() ?: BigDecimal.ZERO
    val minStake = pools.map { it.minStake }.minOrNull() ?: Coins.ZERO
    val apyPercent = apy.setScale(2, RoundingMode.DOWN).stripTrailingZeros()
    val apyFormat = "APY ≈ ${apyPercent}%"
}
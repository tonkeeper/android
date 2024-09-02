package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class PoolInfoEntity(
    val implementation: StakingPool.Implementation,
    val pools: List<PoolEntity>,
    val details: PoolDetailsEntity,
): Parcelable {

    @IgnoredOnParcel
    val apy = pools.maxOfOrNull { it.apy } ?: BigDecimal.ZERO

    @IgnoredOnParcel
    val minStake = pools.minOfOrNull { it.minStake } ?: Coins.ZERO

    val name: String
        get() = implementation.name

    val cycleStart: Long
        get() = pools.minOfOrNull { it.cycleStart } ?: 0
}
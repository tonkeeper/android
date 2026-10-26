package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import io.tonapi.models.PoolInfo
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class PoolEntity(
    val address: String,
    val name: String,
    val implementation: StakingPool.Implementation,
    val minStake: Coins,
    val apy: BigDecimal,
    val bonusApy: BigDecimal? = null,
    val verified: Boolean,
    val cycleStart: Long,
    val cycleEnd: Long,
    val liquidJettonMaster: String?,
    val maxApy: Boolean,
): Parcelable {

    val isTonstakers: Boolean
        get() = implementation == StakingPool.Implementation.LiquidTF

    companion object {

        /** Returns null when the pool's implementation isn't one the app can stake into. */
        fun of(info: PoolInfo, maxApy: Boolean): PoolEntity? {
            val implementation = StakingPool.implementation(info.implementation) ?: return null
            return PoolEntity(
                address = info.address,
                name = info.name,
                implementation = implementation,
                minStake = Coins.of(info.minStake),
                apy = BigDecimal(info.apy),
                verified = info.verified,
                cycleStart = info.cycleStart,
                cycleEnd = info.cycleEnd,
                liquidJettonMaster = info.liquidJettonMaster,
                maxApy = maxApy
            )
        }
    }

}
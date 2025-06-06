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
    val verified: Boolean,
    val cycleStart: Long,
    val cycleEnd: Long,
    val liquidJettonMaster: String?,
    val maxApy: Boolean,
): Parcelable {

    val isTonstakers: Boolean
        get() = implementation == StakingPool.Implementation.LiquidTF

    val isEthena: Boolean
        get() = implementation == StakingPool.Implementation.Ethena

    constructor(
        info: PoolInfo,
        maxApy: Boolean
    ): this(
        address = info.address,
        name = info.name,
        implementation = StakingPool.implementation(info.implementation),
        minStake = Coins.of(info.minStake),
        apy = info.apy,
        verified = info.verified,
        cycleStart = info.cycleStart,
        cycleEnd = info.cycleEnd,
        liquidJettonMaster = info.liquidJettonMaster,
        maxApy = maxApy
    )

    companion object {

        val ethenaTokenAddress = "0:d0e545323c7acb7102653c073377f7e3c67f122eb94d430a250739f109d4a57d"

        val ethena = PoolEntity(
            address = "0:d0e545323c7acb7102653c073377f7e3c67f122eb94d430a250739f109d4a57d",
            name = "Ethena",
            implementation = StakingPool.Implementation.Ethena,
            minStake = Coins.ZERO,
            apy = BigDecimal.ZERO,
            verified = true,
            cycleStart = 0,
            cycleEnd = 0,
            liquidJettonMaster = ethenaTokenAddress,
            maxApy = false
        )
    }
}
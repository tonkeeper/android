package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import io.tonapi.models.PoolInfo
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class PoolEntity(
    val address: String,
    val name: String,
    val implementation: StakingPool.Implementation,
    val minStake: Coins,
    val apy: BigDecimal,
    val verified: Boolean,
): Parcelable {

    constructor(
        info: PoolInfo
    ): this(
        address = info.address,
        name = info.name,
        implementation = StakingPool.implementation(info.implementation),
        minStake = Coins.of(info.minStake),
        apy = info.apy,
        verified = info.verified
    )

}
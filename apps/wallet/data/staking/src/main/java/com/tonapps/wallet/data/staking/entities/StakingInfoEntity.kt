package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import io.tonapi.models.AccountStakingInfo
import io.tonapi.models.PoolImplementation
import io.tonapi.models.PoolInfo
import kotlinx.parcelize.Parcelize

@Parcelize
data class StakingInfoEntity(
    val pool: String,
    val amount: Coins,
    val pendingDeposit: Coins,
    val pendingWithdraw: Coins,
    val readyWithdraw: Coins,
): Parcelable {

    constructor(info: AccountStakingInfo): this(
        pool = info.pool,
        amount = Coins.of(info.amount),
        pendingDeposit = Coins.of(info.pendingDeposit),
        pendingWithdraw = Coins.of(info.pendingWithdraw),
        readyWithdraw = Coins.of(info.readyWithdraw),
    )

    fun calculateBalance(implementation: StakingPool.Implementation): Coins {
        val value = amount + pendingDeposit + readyWithdraw
        if (implementation == StakingPool.Implementation.LiquidTF) {
            return value + pendingWithdraw
        }
        return value
    }

}

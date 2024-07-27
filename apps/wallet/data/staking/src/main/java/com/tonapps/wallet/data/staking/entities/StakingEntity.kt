package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool
import kotlinx.parcelize.Parcelize

@Parcelize
data class StakingEntity(
    val pools: List<PoolInfoEntity>,
    val info: List<StakingInfoEntity>
): Parcelable {

    val poolsWithJettons: List<PoolEntity> by lazy {
        pools.map { it.pools }.flatten().filter {
            it.liquidJettonMaster != null
        }
    }

    val poolsJettonAddresses: List<String> by lazy {
        poolsWithJettons.mapNotNull { it.liquidJettonMaster }
    }

    fun getDetails(implementation: StakingPool.Implementation): PoolDetailsEntity? {
        return pools.find { it.implementation == implementation }?.details
    }

    fun findPoolByAddress(address: String): PoolEntity? {
        return pools.map { it.pools }.flatten().find { it.address == address }
    }

    fun findPoolByTokenAddress(address: String): PoolEntity? {
        return poolsWithJettons.find { it.liquidJettonMaster == address }
    }

    fun getBalance(pool: PoolEntity): Coins {
        val info = info.find { it.pool == pool.address } ?: return Coins.ZERO
        return info.calculateBalance(pool.implementation)
    }

    fun getAmount(pool: PoolEntity): Coins {
        val info = info.find { it.pool == pool.address } ?: return Coins.ZERO
        return info.amount
    }

    fun getReadyWithdraw(pool: PoolEntity): Coins {
        val info = info.find { it.pool == pool.address } ?: return Coins.ZERO
        return info.readyWithdraw
    }
}
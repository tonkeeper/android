package com.tonapps.tonkeeper.core.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class StakedEntity(
    val pool: PoolEntity,
    val amount: Coins,
    val balance: BalanceEntity,
    val readyWithdraw: Coins,
    val fiatBalance: Coins,
    val fiatReadyWithdraw: Coins
) {

    companion object {

        fun create(
            staking: StakingEntity,
            tokens: List<AccountTokenEntity>
        ): List<StakedEntity> {
            val list = mutableListOf<StakedEntity>()
            val activePools = getActivePools(staking, tokens)
            for (pool in activePools) {
                val balance = pool.liquidJettonMaster?.let { jettonMasterAddress ->
                    tokens.find { it.address == jettonMasterAddress }
                }?.balance ?: BalanceEntity(
                    token = TokenEntity.TON,
                    value = staking.getAmount(pool),
                    walletAddress = pool.address,
                    initializedAccount = true,
                )
                list.add(StakedEntity(
                    pool = pool,
                    amount = staking.getAmount(pool),
                    balance = balance,
                    readyWithdraw = staking.getReadyWithdraw(pool),
                    fiatBalance = Coins.ZERO,
                    fiatReadyWithdraw = Coins.ZERO
                ))
            }
            return list
        }

        private fun getActivePools(
            staking: StakingEntity,
            tokens: List<AccountTokenEntity>
        ): List<PoolEntity> {
            val pools = mutableListOf<PoolEntity>()
            for (token in tokens.filter { staking.poolsJettonAddresses.contains(it.address) }) {
                staking.findPoolByTokenAddress(token.address)?.let { pools.add(it) }
            }

            for (info in staking.info) {
                staking.findPoolByAddress(info.pool)?.let { pools.add(it) }
            }
            return pools
        }
    }

}
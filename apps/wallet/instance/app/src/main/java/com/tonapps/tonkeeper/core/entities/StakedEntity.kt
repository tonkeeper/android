package com.tonapps.tonkeeper.core.entities

import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class StakedEntity(
    val pool: PoolEntity,
    val balance: Coins,
    val readyWithdraw: Coins,
    val fiatBalance: Coins,
    val fiatReadyWithdraw: Coins,
    val liquidToken: BalanceEntity? = null
) {

    val isTonstakers: Boolean
        get() = pool.isTonstakers

    val maxApy: Boolean
        get() = pool.maxApy

    companion object {

        suspend fun create(
            staking: StakingEntity,
            tokens: List<AccountTokenEntity>,
            currency: WalletCurrency,
            ratesRepository: RatesRepository
        ): List<StakedEntity> {
            val fiatRates = ratesRepository.getTONRates(currency)
            val list = mutableListOf<StakedEntity>()
            val activePools = getActivePools(staking, tokens)
            for (pool in activePools) {
                if (pool.implementation == StakingPool.Implementation.LiquidTF) {
                    val liquidJettonMaster = pool.liquidJettonMaster ?: continue
                    val token = tokens.find { it.address.equalsAddress(liquidJettonMaster) } ?: continue
                    val rates = ratesRepository.getRates(WalletCurrency.TON, token.address)
                    val balance = rates.convert(token.address, token.balance.value)
                    list.add(StakedEntity(
                        pool = pool,
                        balance = balance,
                        readyWithdraw = Coins.ZERO,
                        fiatBalance = fiatRates.convertTON(balance),
                        fiatReadyWithdraw = Coins.ZERO,
                        liquidToken = token.balance.copy()
                    ))
                } else {
                    val balance = staking.getAmount(pool)
                    val readyWithdraw = staking.getReadyWithdraw(pool)
                    list.add(StakedEntity(
                        pool = pool,
                        balance = balance,
                        readyWithdraw = readyWithdraw,
                        fiatBalance = fiatRates.convertTON(balance),
                        fiatReadyWithdraw = fiatRates.convertTON(readyWithdraw),
                    ))
                }
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
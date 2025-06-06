package com.tonapps.tonkeeper.core.entities

import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
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
    val liquidToken: BalanceEntity? = null,
    val pendingDeposit: Coins,
    val pendingWithdraw: Coins,
    val cycleStart: Long,
    val cycleEnd: Long,
) {

    val isTonstakers: Boolean
        get() = pool.isTonstakers

    val isEthena: Boolean
        get() = pool.isEthena

    val maxApy: Boolean
        get() = pool.maxApy

    companion object {

        suspend fun create(
            wallet: WalletEntity,
            staking: StakingEntity,
            tokens: List<AccountTokenEntity>,
            currency: WalletCurrency,
            ratesRepository: RatesRepository,
            api: API,
        ): List<StakedEntity> {
            val fiatRates = ratesRepository.getTONRates(currency)
            val list = mutableListOf<StakedEntity>()
            val activePools = getActivePools(staking, tokens)
            for (pool in activePools) {
                if (pool.implementation == StakingPool.Implementation.Ethena) {
                    val isEthenaAlready = list.any { it.isEthena }
                    if (isEthenaAlready) {
                        continue
                    }
                    val liquidJettonMaster = pool.liquidJettonMaster ?: continue
                    val token = tokens.find { it.address.equalsAddress(liquidJettonMaster) } ?: continue
                    val tokenBalance = token.balance.value
                    val rates = ratesRepository.getRates(WalletCurrency.USD, token.address)
                    val balance = rates.convert(token.address, tokenBalance)
                    val apy = api.getEthenaStakingAPY(wallet.address)
                    list.add(StakedEntity(
                        pool = pool.copy(
                            apy = apy
                        ),
                        balance = balance,
                        fiatBalance = token.fiat,
                        readyWithdraw = Coins.ZERO,
                        fiatReadyWithdraw = Coins.ZERO,
                        liquidToken = token.balance.copy(),
                        pendingDeposit = Coins.ZERO,
                        pendingWithdraw = Coins.ZERO,
                        cycleStart = pool.cycleStart,
                        cycleEnd = pool.cycleEnd,
                    ))
                } else if (pool.implementation == StakingPool.Implementation.LiquidTF) {
                    val isTonstakersAlready = list.any { it.isTonstakers }
                    if (isTonstakersAlready) {
                        continue
                    }

                    val liquidJettonMaster = pool.liquidJettonMaster ?: continue
                    val token = tokens.find { it.address.equalsAddress(liquidJettonMaster) } ?: continue
                    val rates = ratesRepository.getRates(WalletCurrency.TON, token.address)
                    val balance = rates.convert(token.address, token.balance.value)
                    val readyWithdraw = rates.convert(token.address, staking.getReadyWithdraw(pool))
                    val pendingDeposit = rates.convert(token.address, staking.getPendingDeposit(pool))
                    val pendingWithdraw = rates.convert(token.address, staking.getPendingWithdraw(pool))
                    list.add(StakedEntity(
                        pool = pool,
                        balance = balance,
                        fiatBalance = fiatRates.convertTON(balance),
                        readyWithdraw = readyWithdraw,
                        fiatReadyWithdraw = fiatRates.convertTON(readyWithdraw),
                        liquidToken = token.balance.copy(),
                        pendingDeposit = pendingDeposit,
                        pendingWithdraw = pendingWithdraw,
                        cycleStart = pool.cycleStart,
                        cycleEnd = pool.cycleEnd,
                    ))
                } else {
                    val balance = staking.getAmount(pool)
                    val readyWithdraw = staking.getReadyWithdraw(pool)
                    val pendingDeposit = staking.getPendingDeposit(pool)

                    list.add(StakedEntity(
                        pool = pool,
                        balance = balance,
                        fiatBalance = fiatRates.convertTON(balance),
                        readyWithdraw = readyWithdraw,
                        fiatReadyWithdraw = fiatRates.convertTON(readyWithdraw),
                        pendingDeposit = pendingDeposit,
                        pendingWithdraw = staking.getPendingWithdraw(pool),
                        cycleStart = pool.cycleStart,
                        cycleEnd = pool.cycleEnd,
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

            val tsUSDeEthena = tokens.find { it.address.equalsAddress(PoolEntity.ethenaTokenAddress) }
            if (tsUSDeEthena != null) {
                pools.add(PoolEntity.ethena)
            }

            for (info in staking.info) {
                staking.findPoolByAddress(info.pool)?.let { pools.add(it) }
            }
            return pools
        }
    }

}
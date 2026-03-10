package com.tonapps.wallet.data.staking.source

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.staking.entities.StakingInfoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun load(
        accountId: String, network: TonNetwork, initializedAccount: Boolean
    ): StakingEntity = withContext(Dispatchers.IO) {
        val poolsDeferred = async { loadPools(accountId, network) }
        val infoDeferred = async {
            if (initializedAccount) {
                loadInfo(accountId, network)
            } else {
                emptyList()
            }
        }

        val pools = poolsDeferred.await()
        val info = infoDeferred.await()

        StakingEntity(
            pools = pools,
            info = info
        )
    }

    private fun loadInfo(
        accountId: String, network: TonNetwork
    ): List<StakingInfoEntity> {
        val list = withRetry {
            api.staking(network).getAccountNominatorsPools(accountId).pools
        } ?: return emptyList()
        return list.map { StakingInfoEntity(it) }
    }

    private fun loadPools(
        accountId: String, network: TonNetwork
    ): List<PoolInfoEntity> {
        val response = withRetry {
            api.staking(network).getStakingPools(accountId, includeUnverified = false)
        } ?: return emptyList()

        val maxApyImplementation = response.pools.maxByOrNull { it.apy }?.implementation

        val pools = response.pools.map {
            PoolEntity(it, maxApyImplementation == it.implementation)
        }.map {
            if (it.implementation != StakingPool.Implementation.Whales) {
                it
            } else {
                val cycleStart = if (it.cycleEnd > 0L) it.cycleEnd - 36 * 3600 else 0
                it.copy(cycleStart = cycleStart)
            }
        }

        val implementations = pools.map { it.implementation }.distinct()

        val list = mutableListOf<PoolInfoEntity>()
        for (implementation in implementations) {
            val details = response.implementations[implementation.title] ?: continue
            list.add(
                PoolInfoEntity(
                    implementation = implementation,
                    pools = pools.filter { it.implementation == implementation },
                    details = PoolDetailsEntity(details)
                )
            )
        }

        list.sortWith { a, b ->
            if (a.name.contains("Tonkeeper") && !b.name.contains("Tonkeeper")) {
                return@sortWith -1
            }

            if (b.name.contains("Tonkeeper") && !a.name.contains("Tonkeeper")) {
                return@sortWith 1
            }

            if (a.name.contains("Tonkeeper") && b.name.contains("Tonkeeper")) {
                return@sortWith if (a.name.contains("#1")) -1 else 1
            }

            if (a.apy == b.apy) {
                return@sortWith if (a.cycleStart > b.cycleStart) 1 else -1
            }

            return@sortWith if (a.apy > b.apy) 1 else -1
        }

        return list.reversed()
    }

}
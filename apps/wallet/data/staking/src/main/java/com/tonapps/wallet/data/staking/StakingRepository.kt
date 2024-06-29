package com.tonapps.wallet.data.staking

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StakingRepository(
    private val api: API
) {

    suspend fun pools(
        accountId: String,
        testnet: Boolean
    ): List<PoolInfoEntity> = withContext(Dispatchers.IO) {
        val response = api.staking(testnet).getStakingPools(accountId, includeUnverified = false)
        val pools = response.pools.map { PoolEntity(it) }
        val implementations = pools.map { it.implementation }.distinct()

        val list = mutableListOf<PoolInfoEntity>()
        for (implementation in implementations) {
            val details = response.implementations[implementation.title] ?: continue
            list.add(PoolInfoEntity(
                implementation = implementation,
                pools = pools.filter { it.implementation == implementation },
                details = PoolDetailsEntity(details)
            ))
        }

        list.sortedByDescending { it.apy }
    }

}
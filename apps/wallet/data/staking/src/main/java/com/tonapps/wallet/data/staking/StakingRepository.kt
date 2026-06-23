package com.tonapps.wallet.data.staking

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.staking.source.LocalDataSource
import com.tonapps.wallet.data.staking.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StakingRepository(context: Context, api: API) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    suspend fun get(
        accountId: String,
        network: TonNetwork,
        ignoreCache: Boolean = false,
        initializedAccount: Boolean = true
    ): StakingEntity = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(accountId, network)

        var pools = if (ignoreCache) null else localDataSource.getPools(cacheKey)
        var info = if (ignoreCache) null else localDataSource.getInfo(cacheKey)

        val needsPools = pools == null
        val needsInfo = initializedAccount && info == null

        if (needsPools || needsInfo) {
            val remote = remoteDataSource.load(accountId, network, initializedAccount)
            if (needsPools) {
                pools = remote.pools
                localDataSource.setPools(cacheKey, remote.pools)
            }

            if (initializedAccount) {
                info = remote.info
                localDataSource.setInfo(cacheKey, remote.info)
            }
        }

        StakingEntity(pools = pools, info = info ?: emptyList())
    }

    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}_2"
    }
}

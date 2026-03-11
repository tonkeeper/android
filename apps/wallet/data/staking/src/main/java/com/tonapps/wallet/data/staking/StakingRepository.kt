package com.tonapps.wallet.data.staking

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.staking.entities.StakingInfoEntity
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
        val local: StakingEntity? = if (ignoreCache) null else localDataSource.getCache(cacheKey)
        if (local == null) {
            val remote = remoteDataSource.load(accountId, network, initializedAccount)
            localDataSource.setCache(cacheKey, remote)
            return@withContext remote
        }
        return@withContext local
    }

    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}_2"
    }
}
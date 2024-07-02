package com.tonapps.wallet.data.staking

import android.content.Context
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.source.LocalDataSource
import com.tonapps.wallet.data.staking.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StakingRepository(
    private val context: Context,
    private val api: API
) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    suspend fun pools(
        accountId: String,
        testnet: Boolean
    ): List<PoolInfoEntity> = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(accountId, testnet)
        val local = localDataSource.getCache(cacheKey) ?: emptyList()
        local.ifEmpty {
            val remote = remoteDataSource.load(accountId, testnet)
            localDataSource.setCache(cacheKey, remote)
            remote
        }
    }


    private fun cacheKey(accountId: String, testnet: Boolean): String {
        if (!testnet) {
            return accountId
        }
        return "${accountId}_testnet_2"
    }

}
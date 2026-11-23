package com.tonapps.wallet.data.staking

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.staking.entities.StakingRewardsForecast
import com.tonapps.wallet.data.staking.entities.StakingRewardsPage
import com.tonapps.wallet.data.staking.source.LocalDataSource
import com.tonapps.wallet.data.staking.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class StakingRepository(context: Context, private val api: API) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    suspend fun get(
        accountId: String,
        network: TonNetwork,
        ignoreCache: Boolean = false,
        initializedAccount: Boolean = true
    ): StakingEntity = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(accountId, network)

        var pools = if (ignoreCache) {
            null
        } else {
            localDataSource.getPools(cacheKey)
        }
        var info = if (ignoreCache) {
            null
        } else {
            localDataSource.getInfo(cacheKey)
        }

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

    /** Reads only the cache: null when [get] would have to go to the network. */
    suspend fun getCached(
        accountId: String,
        network: TonNetwork,
        initializedAccount: Boolean = true,
    ): StakingEntity? = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(accountId, network)
        val pools = localDataSource.getPools(cacheKey) ?: return@withContext null
        val info = localDataSource.getInfo(cacheKey)
        if (initializedAccount && info == null) {
            return@withContext null
        }
        StakingEntity(pools = pools, info = info ?: emptyList())
    }

    /** Reads only the cached pools: the nominator info is not needed, and an empty one is not cached. */
    suspend fun getCachedMaxApyFormatted(
        accountId: String,
        network: TonNetwork,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val enabledStaking = api.getConfig(network).enabledStaking
            val maxApy = localDataSource.getPools(cacheKey(accountId, network))
                .orEmpty()
                .filter { enabledStaking.contains(it.implementation.title) }
                .maxOfOrNull { it.apy }
                ?: return@withContext null
            CurrencyFormatter.formatPercent(maxApy).toString()
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getRewards(
        accountId: String,
        period: StakingRewardsPeriod,
        nowMillis: Long,
        limit: Int,
        offset: Int,
    ): StakingRewardsPage? = withContext(Dispatchers.IO) {
        val response = withRetry {
            api.tonstakers.getRewards(
                address = accountId,
                fromSeconds = period.startSeconds(nowMillis),
                limit = limit,
                offset = offset,
                includeTotal = offset == 0,
            )
        } ?: return@withContext null
        StakingRewardsPage.of(response)
    }

    suspend fun getRewardsForecast(
        accountId: String,
        poolApyPercent: BigDecimal,
        nowSeconds: Long,
    ): StakingRewardsForecast? = withContext(Dispatchers.IO) {
        val response = withRetry {
            api.tonstakers.getUpcomingRewards(address = accountId)
        } ?: return@withContext null
        StakingRewardsForecast.of(response, poolApyPercent, nowSeconds)
    }

    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}_2"
    }
}

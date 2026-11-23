package com.tonapps.tonkeeper.ui.screen.staking.rewards

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.StakingRewardsPeriod
import com.tonapps.wallet.data.staking.entities.StakingRewardEntity

class StakingRewardsLoadException : Exception()

class StakingRewardsPagingSource(
    private val stakingRepository: StakingRepository,
    private val accountId: String,
    private val period: StakingRewardsPeriod,
    private val onFirstPageLoaded: (total: Coins?) -> Unit,
) : PagingSource<Int, StakingRewardEntity>() {

    private val createdMillis = System.currentTimeMillis()
    private val loadedIds = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, StakingRewardEntity> {
        val offset = params.key ?: 0
        val page = stakingRepository.getRewards(
            accountId = accountId,
            period = period,
            nowMillis = createdMillis,
            limit = params.loadSize,
            offset = offset,
        ) ?: return LoadResult.Error(StakingRewardsLoadException())
        if (offset == 0) {
            onFirstPageLoaded(page.total)
        }
        val nextOffset = offset + page.fetchedCount
        return LoadResult.Page(
            data = page.items.filter { loadedIds.add(it.id) },
            prevKey = null,
            nextKey = nextOffset.takeIf { page.fetchedCount > 0 && it < page.totalCount },
        )
    }

    override fun getRefreshKey(state: PagingState<Int, StakingRewardEntity>): Int? = null
}

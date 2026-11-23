package com.tonapps.tonkeeper.ui.screen.staking.rewards

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.StakingRewardsPeriod
import com.tonapps.wallet.data.staking.entities.StakingRewardEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.util.Locale

interface StakingRewardsHistoryViewState : MviViewState {
    val period: StateFlow<StakingRewardsPeriod>
    val total: StateFlow<StakingRewardsTotalUi?>
    val loadedPeriod: StateFlow<StakingRewardsPeriod?>
    val rewards: Flow<PagingData<StakingRewardUi>>
}

sealed interface StakingRewardsHistoryAction : MviAction {
    data class SelectPeriod(val value: StakingRewardsPeriod) : StakingRewardsHistoryAction
}

data class StakingRewardsTotalUi(
    val amount: String,
    val fiat: String,
)

data class StakingRewardUi(
    val id: String,
    val period: StakingRewardsPeriod,
    val amount: String,
    val apy: String?,
    val date: String,
    val hiddenBalance: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StakingRewardsHistoryFeature(
    private val wallet: WalletEntity,
    private val stakingRepository: StakingRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
) : GraphViewModel<StakingRewardsHistoryViewState, StakingRewardsHistoryAction>(), StakingRewardsHistoryViewState {

    override val period = actions
        .on<StakingRewardsHistoryAction.SelectPeriod>()
        .map { it.value }
        .cacheState(initialValue = StakingRewardsPeriod.AllTime)

    private val firstPage = MutableStateFlow<FirstPage?>(null)

    private val pagedRewards = period
        .flatMapLatest { period ->
            rewardsPager(period).flow.map { paging -> paging.map { PeriodReward(period, it) } }
        }
        .cachedIn(viewModelScope)

    override val rewards = combine(pagedRewards, settingsRepository.hiddenBalancesFlow) { paging, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        val locale = settingsRepository.getLocale()
        paging.map { it.toUi(hiddenBalance, locale) }
    }

    override val loadedPeriod = firstPage
        .map { it?.period }
        .cacheState(initialValue = null)

    override val total = combine(
        period,
        firstPage,
        settingsRepository.hiddenBalancesFlow,
        settingsRepository.currencyFlow,
    ) { period, firstPage, _, _ -> firstPage?.takeIf { it.period == period } }
        .mapLatest { firstPage -> firstPage?.let { totalUi(it.total) } }
        .flowOn(Async.Io)
        .cacheState(initialValue = null)

    private fun rewardsPager(period: StakingRewardsPeriod): Pager<Int, StakingRewardEntity> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            StakingRewardsPagingSource(
                stakingRepository = stakingRepository,
                accountId = wallet.accountId,
                period = period,
                onFirstPageLoaded = { total -> firstPage.value = FirstPage(period, total) },
            )
        },
    )

    private suspend fun totalUi(amount: Coins?): StakingRewardsTotalUi {
        if (settingsRepository.hiddenBalances) {
            return StakingRewardsTotalUi(amount = HIDDEN_BALANCE, fiat = HIDDEN_BALANCE)
        }
        if (amount == null) {
            return StakingRewardsTotalUi(amount = StakingRewardFormat.NO_VALUE, fiat = "")
        }
        return StakingRewardsTotalUi(
            amount = StakingRewardFormat.amount(amount).toString(),
            fiat = fiatOrEmpty(amount),
        )
    }

    private suspend fun fiatOrEmpty(amount: Coins): String {
        val currency = settingsRepository.currency
        return try {
            val fiat = ratesRepository.getTONRates(wallet.network, currency).convertTON(amount)
            CurrencyFormatter.formatFiat(currency.code, fiat).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            ""
        }
    }

    private fun PeriodReward.toUi(hiddenBalance: Boolean, locale: Locale): StakingRewardUi {
        return StakingRewardUi(
            id = reward.id,
            period = period,
            amount = if (hiddenBalance) {
                HIDDEN_BALANCE
            } else {
                StakingRewardFormat.reward(reward).toString()
            },
            apy = StakingRewardFormat.apy(reward)?.toString(),
            date = StakingRewardFormat.date(reward, locale),
            hiddenBalance = hiddenBalance,
        )
    }

    private data class PeriodReward(
        val period: StakingRewardsPeriod,
        val reward: StakingRewardEntity,
    )

    private data class FirstPage(
        val period: StakingRewardsPeriod,
        val total: Coins?,
    )

    private companion object {
        const val PAGE_SIZE = 50
        const val PREFETCH_DISTANCE = 10
    }
}

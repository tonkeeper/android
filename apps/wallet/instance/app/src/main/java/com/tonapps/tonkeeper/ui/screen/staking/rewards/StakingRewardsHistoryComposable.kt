package com.tonapps.tonkeeper.ui.screen.staking.rewards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.staking.StakingRewardsPeriod
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.flowOf
import ui.components.base.UIKitScaffold
import ui.components.moon.MoonActionSelector
import ui.components.moon.MoonActionSelectorStyle
import ui.components.moon.MoonRefresh
import ui.components.moon.MoonTextShimmer
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase

private const val SKELETON_ROW_COUNT = 6

@Composable
fun StakingRewardsHistoryComposable(
    feature: StakingRewardsHistoryFeature,
    onBack: () -> Unit,
) {
    val period by feature.period.collectAsStateWithLifecycle()
    val total by feature.total.collectAsStateWithLifecycle()
    val loadedPeriod by feature.loadedPeriod.collectAsStateWithLifecycle()
    StakingRewardsHistoryContent(
        period = period,
        total = total,
        loadedPeriod = loadedPeriod,
        rewards = feature.rewards.collectAsLazyPagingItems(),
        onSelectPeriod = { feature.sendAction(StakingRewardsHistoryAction.SelectPeriod(it)) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StakingRewardsHistoryContent(
    period: StakingRewardsPeriod,
    total: StakingRewardsTotalUi?,
    loadedPeriod: StakingRewardsPeriod?,
    rewards: LazyPagingItems<StakingRewardUi>,
    onSelectPeriod: (StakingRewardsPeriod) -> Unit,
    onBack: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()
    val shownPeriod = if (rewards.itemCount > 0) {
        rewards.peek(0)?.period
    } else {
        loadedPeriod
    }
    val showsSelectedPeriod = shownPeriod == period
    val noRewardsOfPeriod = !showsSelectedPeriod || rewards.itemCount == 0
    val firstLoadFailed = rewards.loadState.refresh is LoadState.Error && noRewardsOfPeriod
    val firstLoading = !firstLoadFailed &&
        (!showsSelectedPeriod || rewards.loadState.refresh is LoadState.Loading && rewards.itemCount == 0)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val toastHost = rememberMoonToastHostState()
    val refreshFailedText = stringResource(Localization.staking_rewards_history_failed)
    val refreshError = (rewards.loadState.refresh as? LoadState.Error)?.takeIf { !noRewardsOfPeriod }

    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            toastHost.showToast(refreshFailedText)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StakingRewardsHistoryScaffold(
            period = period,
            total = total,
            rewards = rewards,
            refreshState = refreshState,
            isRefreshing = rewards.loadState.refresh is LoadState.Loading && !noRewardsOfPeriod,
            firstLoading = firstLoading,
            firstLoadFailed = firstLoadFailed,
            navBottom = navBottom,
            onSelectPeriod = onSelectPeriod,
            onBack = onBack,
        )
        MoonToastHost(toastHost)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StakingRewardsHistoryScaffold(
    period: StakingRewardsPeriod,
    total: StakingRewardsTotalUi?,
    rewards: LazyPagingItems<StakingRewardUi>,
    refreshState: PullToRefreshState,
    isRefreshing: Boolean,
    firstLoading: Boolean,
    firstLoadFailed: Boolean,
    navBottom: Dp,
    onSelectPeriod: (StakingRewardsPeriod) -> Unit,
    onBack: () -> Unit,
) {
    UIKitScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MoonTopAppBarSimple(
                modifier = Modifier.statusBarsPadding(),
                title = stringResource(Localization.staking_rewards_history),
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = isRefreshing,
            onRefresh = { rewards.refresh() },
            state = refreshState,
            indicator = {
                MoonRefresh(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = refreshState,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = navBottom + Dimens.offsetMedium),
            ) {
                item(key = "total") {
                    TotalCard(
                        total = total,
                        failed = firstLoadFailed,
                        period = period,
                        onSelectPeriod = onSelectPeriod,
                    )
                }
                rewardsContent(rewards, firstLoading, firstLoadFailed)
            }
        }
    }
}

private fun LazyListScope.rewardsContent(
    rewards: LazyPagingItems<StakingRewardUi>,
    firstLoading: Boolean,
    firstLoadFailed: Boolean,
) {
    when {
        firstLoading -> items(count = SKELETON_ROW_COUNT) { index ->
            RewardSkeletonRow(position = defaultBundleType(SKELETON_ROW_COUNT, index))
        }

        firstLoadFailed -> item(key = "refresh_error") {
            MoonEmptyScreen(
                text = stringResource(Localization.staking_rewards_history_failed),
                type = MoonEmptyScreenType.Error,
                buttonText = stringResource(Localization.retry),
                onButtonClick = rewards::retry,
            )
        }

        else -> {
            items(
                count = rewards.itemCount,
                key = rewards.itemKey { it.id },
            ) { index ->
                val reward = rewards[index] ?: return@items
                RewardRow(reward = reward, position = defaultBundleType(rewards.itemCount, index))
            }
            if (rewards.loadState.append is LoadState.Loading) {
                item(key = "append_loading") {
                    MoonLoaderCell(height = 64.dp)
                }
            }
            if (rewards.loadState.append is LoadState.Error || rewards.loadState.refresh is LoadState.Error) {
                item(key = "append_error") {
                    MoonRetryCell(
                        message = stringResource(Localization.staking_rewards_history_failed),
                        buttonText = stringResource(Localization.retry),
                        onRetry = rewards::retry,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalCard(
    total: StakingRewardsTotalUi?,
    failed: Boolean,
    period: StakingRewardsPeriod,
    onSelectPeriod: (StakingRewardsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = Dimens.offsetMedium)
            .padding(bottom = Dimens.offsetMedium)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerMedium))
            .background(UIKit.colorScheme.background.content)
            .padding(Dimens.offsetMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Localization.staking_total_received),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
            )
            when {
                total != null -> {
                    Text(
                        text = total.amount,
                        style = UIKit.typography.h2,
                        color = UIKit.colorScheme.text.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = total.fiat,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                    )
                }

                failed -> {
                    Text(
                        text = StakingRewardFormat.NO_VALUE,
                        style = UIKit.typography.h2,
                        color = UIKit.colorScheme.text.tertiary,
                    )
                    Text(
                        text = "",
                        style = UIKit.typography.body2,
                    )
                }

                else -> {
                    val phase by rememberShimmerPhase()
                    MoonTextShimmer(text = "000.00 GRAM", style = UIKit.typography.h2, phase = phase)
                    MoonTextShimmer(text = "$000.00", style = UIKit.typography.body2, phase = phase)
                }
            }
        }
        MoonActionSelector(
            items = StakingRewardsPeriod.entries.map { stringResource(it.titleRes) },
            selectedIndex = period.ordinal,
            onSelect = { onSelectPeriod(StakingRewardsPeriod.entries[it]) },
            style = MoonActionSelectorStyle.Tertiary,
        )
    }
}

@Composable
private fun RewardRow(
    reward: StakingRewardUi,
    position: MoonBundlePosition,
) {
    MoonBundleCell(position = position) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.offsetMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RewardIcon()
            Spacer(modifier = Modifier.size(Dimens.offsetMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Localization.staking_reward),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                )
                if (reward.apy != null) {
                    Text(
                        text = stringResource(Localization.staking_reward_apy, reward.apy),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = reward.amount,
                    style = UIKit.typography.label1,
                    color = if (reward.hiddenBalance) {
                        UIKit.colorScheme.text.primary
                    } else {
                        UIKit.colorScheme.accent.green
                    },
                    maxLines = 1,
                )
                Text(
                    text = reward.date,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RewardSkeletonRow(position: MoonBundlePosition) {
    val phase by rememberShimmerPhase()
    MoonBundleCell(position = position) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.offsetMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.offsetMedium),
        ) {
            RewardIcon()
            Column(modifier = Modifier.weight(1f)) {
                MoonTextShimmer(text = "Reward", style = UIKit.typography.label1, phase = phase)
                MoonTextShimmer(text = "APY 00.00%", style = UIKit.typography.body2, phase = phase)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoonTextShimmer(text = "+0.00 GRAM", style = UIKit.typography.label1, phase = phase)
                MoonTextShimmer(text = "00 Oct, 00:00", style = UIKit.typography.body2, phase = phase)
            }
        }
    }
}

@Composable
private fun RewardIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_percent_outline_28),
            contentDescription = null,
            colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary),
        )
    }
}

private val StakingRewardsPeriod.titleRes: Int
    get() = when (this) {
        StakingRewardsPeriod.AllTime -> Localization.staking_period_all_time
        StakingRewardsPeriod.Month -> Localization.staking_period_1_month
        StakingRewardsPeriod.HalfYear -> Localization.staking_period_6_months
        StakingRewardsPeriod.Year -> Localization.staking_period_1_year
    }
private val previewTotal = StakingRewardsTotalUi(amount = "146.08 GRAM", fiat = "$268.78")

private val previewRewards = listOf(
    StakingRewardUi(id = "1", period = StakingRewardsPeriod.AllTime, amount = "+1.25 GRAM", apy = "12.72%", date = "21 Oct, 06:00", hiddenBalance = false),
    StakingRewardUi(id = "2", period = StakingRewardsPeriod.AllTime, amount = "+1.25 GRAM", apy = "12.68%", date = "20 Oct, 12:00", hiddenBalance = false),
    StakingRewardUi(id = "3", period = StakingRewardsPeriod.AllTime, amount = "+0 GRAM", apy = "12.74%", date = "19 Oct, 18:00", hiddenBalance = false),
)

private val notLoading = LoadState.NotLoading(endOfPaginationReached = true)

private fun previewLoadStates(
    refresh: LoadState = notLoading,
    append: LoadState = notLoading,
): LoadStates = LoadStates(refresh = refresh, prepend = notLoading, append = append)

@Composable
private fun StakingRewardsHistoryPreviewContent(
    total: StakingRewardsTotalUi?,
    rewards: List<StakingRewardUi>,
    loadedPeriod: StakingRewardsPeriod? = StakingRewardsPeriod.AllTime,
    loadStates: LoadStates = previewLoadStates(),
) {
    ThemedPreview(isDarkOnly = true) {
        StakingRewardsHistoryContent(
            period = StakingRewardsPeriod.AllTime,
            total = total,
            loadedPeriod = loadedPeriod,
            rewards = flowOf(PagingData.from(rewards, loadStates)).collectAsLazyPagingItems(),
            onSelectPeriod = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun StakingRewardsHistoryPreview() {
    StakingRewardsHistoryPreviewContent(total = previewTotal, rewards = previewRewards)
}

@Preview
@Composable
private fun StakingRewardsHistoryHiddenBalancePreview() {
    StakingRewardsHistoryPreviewContent(
        total = StakingRewardsTotalUi(amount = HIDDEN_BALANCE, fiat = HIDDEN_BALANCE),
        rewards = previewRewards.map { it.copy(amount = HIDDEN_BALANCE, hiddenBalance = true) },
    )
}

@Preview
@Composable
private fun StakingRewardsHistoryLoadingPreview() {
    StakingRewardsHistoryPreviewContent(
        total = null,
        rewards = emptyList(),
        loadedPeriod = null,
        loadStates = previewLoadStates(refresh = LoadState.Loading),
    )
}

@Preview
@Composable
private fun StakingRewardsHistoryErrorPreview() {
    StakingRewardsHistoryPreviewContent(
        total = null,
        rewards = emptyList(),
        loadedPeriod = null,
        loadStates = previewLoadStates(refresh = LoadState.Error(StakingRewardsLoadException())),
    )
}

@Preview
@Composable
private fun StakingRewardsHistoryEmptyPeriodPreview() {
    StakingRewardsHistoryPreviewContent(
        total = StakingRewardsTotalUi(amount = "0 GRAM", fiat = "$0"),
        rewards = emptyList(),
    )
}

@Preview
@Composable
private fun StakingRewardsHistoryAppendErrorPreview() {
    StakingRewardsHistoryPreviewContent(
        total = previewTotal,
        rewards = previewRewards,
        loadStates = previewLoadStates(append = LoadState.Error(StakingRewardsLoadException())),
    )
}

@Preview
@Composable
private fun StakingRewardsHistoryRefreshErrorPreview() {
    StakingRewardsHistoryPreviewContent(
        total = previewTotal,
        rewards = previewRewards,
        loadStates = previewLoadStates(refresh = LoadState.Error(StakingRewardsLoadException())),
    )
}

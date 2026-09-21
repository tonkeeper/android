package com.tonapps.wallet.features.events.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.FilterSwitchItem
import com.tonapps.core.components.FiltersSheet
import com.tonapps.core.components.chainImageResourceUrl
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.features.events.components.ActivityEventCell
import com.tonapps.wallet.features.events.components.ActivityHeaderCell
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.data.nftFor
import com.tonapps.wallet.features.events.screens.details.EventDetailsModal
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.toPersistentList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.components.moon.MoonActionSelector
import ui.components.moon.MoonChipBarCell
import ui.components.moon.MoonRefresh
import ui.components.moon.MoonItem
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.Dimens
import ui.theme.UIKit

private const val ALL_FILTER_ID = -1
private const val SHIMMER_KEY = 2
private const val LOADING_MORE_KEY = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    assetId: String?,
    onBack: () -> Unit,
    onOpenDeposit: () -> Unit = {},
    onOpenNft: (address: String) -> Unit = {},
) {
    val feature = koinViewModel<EventsFeature> { parametersOf(assetId) }
    val pagingItems = feature.activitiesFlow.collectAsLazyPagingItems()
    val typeSelector by feature.typeSelector.collectAsState()
    val hasAnyActivity by feature.hasAnyActivity.collectAsState()
    val chainFilter by feature.chainFilter.collectAsState()
    val resolvedNfts by feature.resolvedNfts.collectAsState()
    val wallet by feature.wallet.collectAsState(initial = null)
    val isOnline by feature.isOnline.collectAsState()
    val hideDust by feature.hideDust.collectAsState()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isLoading = pagingItems.loadState.refresh is LoadState.Loading ||
        (pagingItems.loadState.append is LoadState.Loading && pagingItems.itemCount == 0)
    val isNoConnection = !isOnline && !isLoading && pagingItems.itemCount == 0
    val isError = !isLoading && !isNoConnection && pagingItems.itemCount == 0 &&
        (pagingItems.loadState.refresh is LoadState.Error ||
            pagingItems.loadState.append is LoadState.Error)
    val isEmpty = !isLoading && !isNoConnection && !isError && pagingItems.itemCount == 0
    var selectedActivity by remember { mutableStateOf<HistoryEventEntity?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    var userPullRefresh by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current

    fun invalidateAndRefresh() {
        feature.invalidateCache()
        pagingItems.refresh()
    }

    LaunchedEffect(chainFilter, typeSelector.selected, hideDust) {
        snapshotFlow { pagingItems.loadState.refresh }
            .first { it !is LoadState.Loading }
        listState.scrollToItem(0)
    }

    LaunchedEffect(pagingItems.loadState.refresh) {
        if (pagingItems.loadState.refresh !is LoadState.Loading) {
            userPullRefresh = false
        }
    }

    LaunchedEffect(Unit) {
        feature.isOnline
            .drop(1)
            .filter { it }
            .collect { invalidateAndRefresh() }
    }

    LaunchedEffect(lifecycle) {
        feature.activitiesRefreshSignal
            .flowWithLifecycle(lifecycle.lifecycle)
            .filter { pagingItems.loadState.refresh !is LoadState.Loading }
            .filter { !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 }
            .collect { invalidateAndRefresh() }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val contentPadding = PaddingValues(
        bottom = navBarPadding.calculateBottomPadding() + 80.dp,
    )

    MoonScaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .statusBarsPadding(),
        topBar = {
            MoonTopAppBar(
                title = stringResource(Localization.history),
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                // Design puts the Filters entry on the main History only. The pref stays
                // global, so a token's history is filtered too — same as the assets toggle
                // governing the wallet list from Manage crypto.
                actionIconRes = if (assetId == null) {
                    UIKitIcon.ic_sliders_16
                } else {
                    null
                },
                onActionClick = { showFilters = true },
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        if (assetId == null) {
            ChainFiltersCell(
                chainFilter = chainFilter,
                onChainSelected = feature::onChainSelected,
            )
        }

        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = userPullRefresh && isLoading,
            onRefresh = {
                userPullRefresh = true
                invalidateAndRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                MoonRefresh(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                )
            },
        ) {
            if (isLoading && pagingItems.itemCount == 0) {
                ActivityListShimmer(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    state = listState,
                )
            } else if (isNoConnection) {
                MoonEmptyScreen(
                    text = stringResource(Localization.no_internet_connection),
                    type = MoonEmptyScreenType.Error,
                    buttonText = stringResource(Localization.retry),
                    onButtonClick = ::invalidateAndRefresh,
                )
            } else if (isError) {
                MoonEmptyScreen(
                    text = stringResource(Localization.something_went_wrong),
                    type = MoonEmptyScreenType.Error,
                    buttonText = stringResource(Localization.retry),
                    onButtonClick = pagingItems::retry,
                )
            } else if (isEmpty) {
                if (assetId == null && typeSelector.selected == EventsTypeFilter.All && !hideDust) {
                    MoonEmptyScreen(
                        text = stringResource(Localization.empty_history_title),
                        description = stringResource(Localization.empty_history_subtitle),
                        type = MoonEmptyScreenType.EmptyActivity,
                        buttonText = stringResource(Localization.add_funds),
                        buttonIconRes = null,
                        onButtonClick = onOpenDeposit,
                    )
                } else {
                    MoonEmptyScreen(
                        text = stringResource(Localization.cant_find_anything),
                        type = MoonEmptyScreenType.EmptyActivity,
                    )
                }
            } else {
                ActivityList(
                    modifier = Modifier.fillMaxSize(),
                    pagingItems = pagingItems,
                    resolvedNfts = resolvedNfts,
                    contentPadding = contentPadding,
                    state = listState,
                    onActivityClick = { selectedActivity = it },
                    onNftClick = onOpenNft,
                )
            }

            if (!isEmpty ||
                typeSelector.selected != EventsTypeFilter.All ||
                hasAnyActivity == true ||
                hideDust
            ) {
                ActivityTypeFilterMenu(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    items = typeSelector.items,
                    selectedIndex = typeSelector.selectedIndex,
                    onSelect = feature::onTypeFilterSelected,
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            item = FilterSwitchItem(
                title = stringResource(Localization.hide_tiny_transfers),
                subtitle = stringResource(Localization.hide_tiny_transfers_desc),
                checked = hideDust,
                onToggle = { feature.onHideDustChanged() },
            ),
            onClose = { showFilters = false },
        )
    }

    selectedActivity?.let { activity ->
        EventDetailsModal(
            event = activity,
            wallet = wallet,
            nft = resolvedNfts.nftFor(activity),
            onDismiss = { selectedActivity = null },
        )
    }
}

@Composable
private fun ActivityListShimmer(
    contentPadding: PaddingValues,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        state = state,
        userScrollEnabled = false,
    ) {
        item(
            key = "shimmer_header",
            contentType = SHIMMER_KEY,
        ) {
            ActivityHeaderCell()
        }
        items(
            10,
            key = { "shimmer_$it" },
            contentType = { SHIMMER_KEY },
        ) { index ->
            ActivityEventCell(
                position = defaultBundleType(10, index),
            )
        }
    }
}

@Composable
private fun ActivityList(
    pagingItems: LazyPagingItems<WalletActivityListItem>,
    resolvedNfts: Map<String, HistoryNft>,
    contentPadding: PaddingValues,
    state: LazyListState,
    onActivityClick: (HistoryEventEntity) -> Unit,
    onNftClick: (address: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        state = state,
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index ->
                when (val item = pagingItems.peek(index)) {
                    is WalletActivityListItem.DateHeader -> item.listKey
                    is WalletActivityListItem.Entry -> item.activity.listKey
                    null -> "activity_ph_$index"
                }
            },
            contentType = { index ->
                when (pagingItems.peek(index)) {
                    is WalletActivityListItem.DateHeader -> "wallet_activity_header"
                    is WalletActivityListItem.Entry -> "wallet_activity"
                    else -> "wallet_activity_ph"
                }
            },
        ) { index ->
            when (val row = pagingItems[index]) {
                is WalletActivityListItem.DateHeader -> ActivityHeaderCell(text = row.title)

                is WalletActivityListItem.Entry -> {
                    val activity = row.activity
                    val nft = resolvedNfts.nftFor(activity)
                    val position = remember(
                        index,
                        pagingItems.itemCount,
                        pagingItems.loadState.append,
                        activity,
                    ) {
                        val append = pagingItems.loadState.append
                        moonBundlePositionForWalletActivityEntry(
                            previous = if (index > 0) {
                                pagingItems.peek(index - 1)
                            } else {
                                null
                            },
                            next = if (index + 1 < pagingItems.itemCount) {
                                pagingItems.peek(index + 1)
                            } else {
                                null
                            },
                            appendIsLoading = append is LoadState.Loading,
                            appendEndReached = append is LoadState.NotLoading &&
                                append.endOfPaginationReached,
                        )
                    }
                    ActivityEventCell(
                        activity = activity,
                        nft = nft,
                        position = position,
                        onClick = { onActivityClick(activity) },
                        onNftClick = nft?.let { resolved ->
                            { onNftClick(resolved.address) }
                        },
                    )
                }

                null -> Unit
            }
        }
        if (pagingItems.loadState.append is LoadState.Loading) {
            item(LOADING_MORE_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = UIKit.colorScheme.accent.blue,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainFiltersCell(
    chainFilter: Chain?,
    onChainSelected: (Chain?) -> Unit,
) {
    val context = LocalContext.current
    val allStr = stringResource(Localization.all)
    val mainnetChains = remember {
        Chain.all.filter {
            it.network.mode == Network.Mode.Mainnet && it.toApiChain() != null
        }
    }
    val chainFilters = remember(allStr, mainnetChains) {
        val items = listOf(MoonItem(id = ALL_FILTER_ID, title = allStr)) +
                mainnetChains.mapIndexed { index, chain ->
                    MoonItem(
                        id = index,
                        title = chain.name,
                        image = chain.coin.chainImageResourceUrl(context),
                    )
                }
        items.toPersistentList()
    }

    MoonChipBarCell(
        filters = chainFilters,
        selectedId = chainFilter?.let { c ->
            mainnetChains.indexOf(c).takeIf { it >= 0 }
        } ?: ALL_FILTER_ID,
        onSelect = { item ->
            val chain = if (item.id == ALL_FILTER_ID) {
                null
            } else {
                mainnetChains.getOrNull(item.id)
            }
            onChainSelected(chain)
        },
    )
}

@Composable
private fun ActivityTypeFilterMenu(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.heightBar + navBarPadding.calculateBottomPadding())
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        UIKit.colorScheme.background.page,
                    ),
                ),
            )
            .padding(bottom = navBarPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        MoonActionSelector(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
    }
}

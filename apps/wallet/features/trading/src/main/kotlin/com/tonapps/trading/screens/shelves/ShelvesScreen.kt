package com.tonapps.trading.screens.shelves

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.core.navigation.PortfolioSearchSort
import com.tonapps.icu.Formatter
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.trading.displayChainName
import com.tonapps.trading.percentDiffColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRefSummary
import io.tradingapi.models.AssetType
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketItem
import io.tradingapi.models.MarketListKey
import io.tradingapi.models.MarketMetricsSummary
import io.tradingapi.models.MultichainShelfGroup
import io.tradingapi.models.ShelfConfig
import io.tradingapi.models.ShelfConfigSeeAll
import io.tradingapi.models.ShelfGroup
import io.tradingapi.models.ShelfType
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import ui.components.moon.MoonActionSelector
import ui.components.moon.MoonActionSelectorStyle
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonContentTabs
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonRefresh
import ui.components.moon.MoonTabItem
import ui.components.moon.MoonVerificationBadge
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.popup.ActionMenuHorizontalAlignment
import ui.painterResource
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.components.BannersViewer
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.wallet.api.entity.BannerEntity
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
fun ShelvesScreen(
    feature: ShelvesFeature,
    onOpenSearch: () -> Unit,
    onOpenSeeAll: (AssetsTab, PortfolioSearchSort, Network.Type?) -> Unit,
    onOpenAssetDetails: (MarketItem) -> Unit,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
    onOpenLink: (String) -> Unit,
    scrollToShelfKey: MarketListKey? = null,
    onScrollToShelfHandled: () -> Unit = {},
) {
    val shelfGroups by feature.shelfGroupsFlow.collectAsStateWorkaround()
    val favoriteAssets by feature.favoritesFlow.collectAsStateWorkaround()
    val shelvesError by feature.shelvesError.collectAsStateWorkaround()
    val isRefreshing by feature.isRefreshing.collectAsStateWorkaround()
    val selections = feature.selections
    val banners by feature.raffleBanners.collectAsStateWorkaround()
    val isPerpsVisible by feature.isPerpsVisible.collectAsStateWorkaround()
    val perpsShelfMarkets by feature.perpsShelfMarkets.collectAsStateWorkaround()

    val raffleEvents = AnalyticsHelper.Default.events.mysteryRaffle
    LaunchedEffect(banners.isNotEmpty()) {
        if (banners.isNotEmpty()) {
            raffleEvents.raffleBannerView(MysteryRaffleSource.Trade)
        }
    }

    ShelvesContent(
        isLoading = shelfGroups == null,
        isRefreshing = isRefreshing,
        shelfGroups = shelfGroups,
        favoriteAssets = favoriteAssets,
        shelvesError = shelvesError,
        selections = selections,
        onNetworkSelected = feature::onNetworkSelected,
        onTabSelected = feature::onTabSelected,
        onSelectShelf = feature::selectShelf,
        banners = banners,
        onBannerClick = { payload ->
            raffleEvents.raffleBannerClick(MysteryRaffleSource.Trade)
            onOpenLink(payload.withRaffleSource(MysteryRaffleSource.Trade.key))
        },
        onHideBanner = { banner ->
            raffleEvents.raffleBannerDismiss(MysteryRaffleSource.Trade)
            feature.hideBanner(banner)
        },
        onRefresh = feature::refresh,
        onOpenSearch = onOpenSearch,
        onOpenSeeAll = onOpenSeeAll,
        onOpenAssetDetails = {
            feature.trackAssetClick(it)
            onOpenAssetDetails(it)
        },
        onOpenPerps = onOpenPerps,
        onOpenPerpMarket = onOpenPerpMarket,
        isPerpsVisible = isPerpsVisible,
        perpsShelfMarkets = perpsShelfMarkets,
        onRemoveFavorites = feature::removeFavorites,
        scrollToShelfKey = scrollToShelfKey,
        onScrollToShelfHandled = onScrollToShelfHandled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelvesContent(
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    shelfGroups: List<MultichainShelfGroup>?,
    favoriteAssets: List<MarketItem>,
    shelvesError: Boolean,
    selections: Map<String, ShelfSelection>,
    onNetworkSelected: (String, String) -> Unit,
    onTabSelected: (String, MarketListKey) -> Unit,
    onSelectShelf: (String, String?, MarketListKey) -> Unit,
    onRefresh: () -> Unit,
    banners: List<BannerEntity> = emptyList(),
    onBannerClick: (String) -> Unit = {},
    onHideBanner: (BannerEntity) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenSeeAll: (AssetsTab, PortfolioSearchSort, Network.Type?) -> Unit,
    onOpenAssetDetails: (MarketItem) -> Unit,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit = { _, _ -> },
    isPerpsVisible: Boolean,
    perpsShelfMarkets: List<PerpMarket> = emptyList(),
    onRemoveFavorites: (Collection<String>) -> Unit,
    scrollToShelfKey: MarketListKey? = null,
    onScrollToShelfHandled: () -> Unit = {},
) {
    MoonScaffold(
        topBar = {
            MoonSearchCell(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp),
                placeholder = stringResource(Localization.search_by_ticker),
                onClick = onOpenSearch
            )
        }
    ) {
        val pullToRefreshState = rememberPullToRefreshState()
        var userPullRefresh by remember { mutableStateOf(false) }

        LaunchedEffect(isRefreshing) {
            if (!isRefreshing) {
                userPullRefresh = false
            }
        }

        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = userPullRefresh && isRefreshing,
            onRefresh = {
                userPullRefresh = true
                onRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                MoonRefresh(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                )
            },
        ) {
            when {
                isLoading -> ShelvesShimmer()

                shelvesError -> Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.heightBar),
                ) {
                    if (banners.isNotEmpty()) {
                        BannersViewer(
                            banners = banners,
                            onClick = { button -> onBannerClick(button.payload) },
                            onHide = onHideBanner,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (favoriteAssets.isNotEmpty()) {
                        FavoriteTokensSection(
                            items = favoriteAssets,
                            onAssetClick = onOpenAssetDetails,
                            onRemoveFavorites = onRemoveFavorites,
                        )
                    }
                    MoonEmptyScreen(
                        text = stringResource(Localization.something_went_wrong),
                        buttonText = stringResource(Localization.retry),
                        onButtonClick = onRefresh,
                    )
                }

                else -> {
                    val groups = shelfGroups.orEmpty()
                    val listState = rememberLazyListState()
                    val isAtTop by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    var wasAtTopWithoutFavorites by remember { mutableStateOf(true) }
                    var hadFavorites by remember { mutableStateOf(favoriteAssets.isNotEmpty()) }

                    LaunchedEffect(isAtTop, favoriteAssets.isEmpty()) {
                        if (favoriteAssets.isEmpty()) {
                            wasAtTopWithoutFavorites = isAtTop
                        }
                    }

                    LaunchedEffect(favoriteAssets.isNotEmpty()) {
                        val hasFavorites = favoriteAssets.isNotEmpty()
                        if (hasFavorites && !hadFavorites && wasAtTopWithoutFavorites) {
                            listState.animateScrollToItem(0)
                        }
                        hadFavorites = hasFavorites
                    }

                    var wasAtTopWithoutBanners by remember { mutableStateOf(true) }
                    var hadBanners by remember { mutableStateOf(banners.isNotEmpty()) }

                    LaunchedEffect(isAtTop, banners.isEmpty()) {
                        if (banners.isEmpty()) {
                            wasAtTopWithoutBanners = isAtTop
                        }
                    }

                    LaunchedEffect(banners.isNotEmpty()) {
                        val hasBanners = banners.isNotEmpty()
                        if (hasBanners && !hadBanners && wasAtTopWithoutBanners) {
                            listState.scrollToItem(0)
                        }
                        hadBanners = hasBanners
                    }

                    LaunchedEffect(scrollToShelfKey, groups, favoriteAssets.isEmpty(), banners.isEmpty()) {
                        val key = scrollToShelfKey ?: return@LaunchedEffect
                        val targetIndex = groups.indexOfFirst { multichainGroup ->
                            multichainGroup.groups.any { shelfGroup ->
                                shelfGroup.items.any { it.key == key }
                            }
                        }
                        if (targetIndex >= 0) {
                            val targetGroup = groups[targetIndex]
                            val subgroupName = targetGroup.groups.firstOrNull { shelfGroup ->
                                shelfGroup.items.any { it.key == key }
                            }?.name
                            onSelectShelf(targetGroup.id, subgroupName, key)
                            val bannersOffset = if (banners.isNotEmpty()) 1 else 0
                            val favoritesOffset = if (favoriteAssets.isNotEmpty()) 1 else 0
                            listState.animateScrollToItem(targetIndex + bannersOffset + favoritesOffset)
                        }
                        onScrollToShelfHandled()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = Dimens.heightBar),
                    ) {
                        if (banners.isNotEmpty()) {
                            item(key = "raffle_banner", contentType = "banners") {
                                BannersViewer(
                                    banners = banners,
                                    onClick = { button -> onBannerClick(button.payload) },
                                    onHide = onHideBanner,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        if (favoriteAssets.isNotEmpty()) {
                            item(key = "favorite_tokens") {
                                Box(modifier = Modifier.graphicsLayer { clip = false }) {
                                    FavoriteTokensSection(
                                        items = favoriteAssets,
                                        onAssetClick = onOpenAssetDetails,
                                        onRemoveFavorites = onRemoveFavorites,
                                    )
                                }
                            }
                        }

                        if (isPerpsVisible) {
                            item(key = "perps_shelf") {
                                PerpsShelfSection(
                                    markets = perpsShelfMarkets,
                                    onOpenPerps = onOpenPerps,
                                    onOpenPerpMarket = onOpenPerpMarket,
                                )
                            }
                        }

                        items(
                            items = groups,
                            key = { group -> group.id },
                        ) { multichainGroup ->
                            MultichainShelfGroupItem(
                                multichainGroup = multichainGroup,
                                selection = selections[multichainGroup.id],
                                listState = listState,
                                onNetworkSelected = { onNetworkSelected(multichainGroup.id, it) },
                                onTabSelected = { onTabSelected(multichainGroup.id, it) },
                                onAssetClick = { onOpenAssetDetails(it) },
                                onOpenSeeAll = onOpenSeeAll,
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun PerpsShelfSection(
    markets: List<PerpMarket>,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        MoonBundleTitleCell(
            title = stringResource(Localization.perps_trending),
            onClick = onOpenPerps,
        )
        if (markets.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(shape = Shapes.medium, color = UIKit.colorScheme.background.content)
                    .padding(8.dp),
            ) {
                PerpsShelfGrid(markets = markets, onOpenPerpMarket = onOpenPerpMarket)
            }
        }
    }
}

@Composable
private fun PerpsShelfGrid(
    markets: List<PerpMarket>,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        markets.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                rowItems.forEach { market ->
                    Box(modifier = Modifier.weight(1f)) {
                        PerpsShelfItem(
                            market = market,
                            onClick = { onOpenPerpMarket(market.marketIndex, market.symbol) },
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PerpsShelfItem(
    market: PerpMarket,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.medium12)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerpsAssetIcon(symbol = market.symbol, iconUrl = market.iconUrl, size = 56.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = market.symbol,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (market.maxLeverage > 0) {
                Text(
                    text = stringResource(Localization.perps_leverage_pattern, market.maxLeverage),
                    style = UIKit.typography.body3,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
        val change = market.priceChange24hPercent
        Text(
            text = change?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
            style = UIKit.typography.body3,
            color = perpsChangeColor(change),
            maxLines = 1,
        )
    }
}

private val FavoriteTokenOverflow = 4.dp

@Composable
private fun FavoriteTokensSection(
    items: List<MarketItem>,
    onAssetClick: (MarketItem) -> Unit,
    onRemoveFavorites: (Collection<String>) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var pendingRemovalIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(items) {
        pendingRemovalIds = pendingRemovalIds.filterTo(linkedSetOf()) { id ->
            items.any { it.asset.id == id }
        }
        if (items.isEmpty()) {
            isEditing = false
            pendingRemovalIds = emptySet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Localization.favorite_tokens),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth()
                    .defaultMinSize(48.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (isEditing) {
                                onRemoveFavorites(pendingRemovalIds)
                            }
                            isEditing = !isEditing
                        },
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (isEditing) {
                    Text(
                        text = stringResource(Localization.done),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.accent,
                    )
                } else {
                    MoonItemIcon(
                        modifier = Modifier.padding(end = 8.dp),
                        painter = painterResource(UIKitIcon.ic_ellipsis_16),
                        color = UIKit.colorScheme.icon.secondary
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = items.filter { it.asset.id !in pendingRemovalIds },
                key = { it.asset.id },
            ) { item ->
                Box(modifier = Modifier.graphicsLayer { clip = false }) {
                    FavoriteTokenCard(
                        item = item,
                        isEditing = isEditing,
                        onClick = { onAssetClick(item) },
                        onMarkForRemoval = {
                            pendingRemovalIds = pendingRemovalIds + item.asset.id
                        },
                    )
                }
            }
        }
    }
}

private val FavoriteTokenCardWidth = 100.dp
private const val FavoriteRemoveAnimationMillis = 300

@Composable
private fun FavoriteTokenCard(
    item: MarketItem,
    isEditing: Boolean,
    onClick: () -> Unit,
    onMarkForRemoval: () -> Unit,
) {
    var isRemoving by remember(item.asset.id) { mutableStateOf(false) }

    val width by animateDpAsState(
        targetValue = if (isRemoving) 0.dp else FavoriteTokenCardWidth,
        animationSpec = tween(FavoriteRemoveAnimationMillis),
        label = "favoriteCardWidth",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(FavoriteRemoveAnimationMillis),
        label = "favoriteCardAlpha",
    )

    LaunchedEffect(isRemoving) {
        if (isRemoving) {
            delay(FavoriteRemoveAnimationMillis.toLong())
            onMarkForRemoval()
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                this.alpha = alpha
                clip = false
            },
    ) {
        Box(modifier = Modifier.width(FavoriteTokenCardWidth)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.medium12)
                    .background(color = UIKit.colorScheme.background.content),
            ) {
                AssetItem(
                    padding = remember { PaddingValues(horizontal = 8.dp, vertical = 16.dp) },
                    item = item,
                    onClick = if (isEditing) null else onClick,
                )
            }

            if (isEditing && !isRemoving) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = FavoriteTokenOverflow, y = -FavoriteTokenOverflow)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color = UIKit.colorScheme.background.contentTint)
                        .clickable { isRemoving = true },
                    contentAlignment = Alignment.Center,
                ) {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_close_small_16),
                        color = UIKit.colorScheme.icon.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultichainShelfGroupItem(
    multichainGroup: MultichainShelfGroup,
    selection: ShelfSelection?,
    listState: LazyListState,
    onNetworkSelected: (String) -> Unit,
    onTabSelected: (MarketListKey) -> Unit,
    onAssetClick: (MarketItem) -> Unit,
    onOpenSeeAll: (AssetsTab, PortfolioSearchSort, Network.Type?) -> Unit,
) {
    val nestedGroups = multichainGroup.groups
    val resolved = resolveShelfSelection(multichainGroup, selection)

    val seeAllConfig = resolved.selectedConfig?.takeIf { it.seeAll.enabled }
    val onTitleClick: (() -> Unit)? = seeAllConfig?.let { config ->
        {
            onOpenSeeAll(
                config.seeAll.route,
                config.searchSort(),
                chainNetworkTypeForName(resolved.selectedGroup.name),
            )
        }
    }

    ShelfContainer(
        title = multichainGroup.name,
        onTitleClick = onTitleClick,
        rightContent = if (nestedGroups.size < 2) {
            null
        } else {
            {
                MoonActionSelector(
                    items = nestedGroups.map { it.name.displayChainName() },
                    selectedIndex = nestedGroups.indexOf(resolved.selectedGroup),
                    onSelect = { onNetworkSelected(nestedGroups[it].name) },
                    style = MoonActionSelectorStyle.Secondary,
                    leadingIcon = painterResource(UIKitIcon.ic_globe_16),
                    menuAlignment = ActionMenuHorizontalAlignment.Start,
                    menuOffset = DpOffset(0.dp, 4.dp),
                )
            }
        },
    ) {
        ShelfGroupContent(
            group = resolved.selectedGroup,
            selectedKey = resolved.selectedTabKey,
            listState = listState,
            onKeySelected = onTabSelected,
            onAssetClick = onAssetClick,
        )
    }
}

private data class ResolvedShelfSelection(
    val selectedGroup: ShelfGroup,
    val selectedConfig: ShelfConfig?,
) {
    val selectedTabKey: MarketListKey? get() = selectedConfig?.key
}

private fun resolveShelfSelection(
    group: MultichainShelfGroup,
    selection: ShelfSelection?,
): ResolvedShelfSelection {
    val nestedGroups = group.groups
    val selectedGroup = selection?.networkName
        ?.let { name -> nestedGroups.firstOrNull { it.name == name } }
        ?: nestedGroups.first()
    val selectedConfig = selection?.tabKey
        ?.let { key -> selectedGroup.items.firstOrNull { it.key == key } }
        ?: selectedGroup.items.firstOrNull()
    return ResolvedShelfSelection(selectedGroup, selectedConfig)
}

private fun ShelfConfig.searchSort(): PortfolioSearchSort = when (key) {
    MarketListKey.market_cap -> PortfolioSearchSort.MARKET_CAP
    MarketListKey.volume -> PortfolioSearchSort.VOLUME
    MarketListKey.top_gainers -> PortfolioSearchSort.PRICE_DIFF_DESC
    MarketListKey.top_losers -> PortfolioSearchSort.PRICE_DIFF_ASC
    else -> PortfolioSearchSort.MARKET_CAP
}

private fun chainNetworkTypeForName(name: String): Network.Type? =
    Chain.all.firstOrNull { it.coin.name.equals(name, ignoreCase = true) }?.network?.type

private const val ShelfHeightAnimMs = 300
private const val ShelfAssetsExitMs = 150
private const val ShelfAssetsEnterMs = 360
private const val ShelfAssetsEnterDelayMs = 120

private val ShelfAssetsEnterEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

private data class ShelfAssetsState(
    val groupName: String,
    val selectedKey: MarketListKey?,
    val items: List<MarketItem>,
)

private fun AnimatedContentTransitionScope<ShelfAssetsState>.shelfAssetsTransition(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = ShelfAssetsEnterMs,
                delayMillis = ShelfAssetsEnterDelayMs,
                easing = ShelfAssetsEnterEasing,
            ),
        ) togetherWith fadeOut(
            animationSpec = tween(
                durationMillis = ShelfAssetsExitMs,
                easing = EaseOut,
            ),
        )
        ) using SizeTransform(clip = true) { _, _ ->
        tween(
            durationMillis = ShelfHeightAnimMs,
            easing = FastOutSlowInEasing,
        )
    }

@Composable
private fun Modifier.scrollOnHeightGrow(listState: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    var previousHeightPx by remember { mutableIntStateOf(0) }
    return onSizeChanged { size ->
        val delta = size.height - previousHeightPx
        if (previousHeightPx > 0 && delta > 0) {
            scope.launch { listState.scrollBy(delta.toFloat()) }
        }
        previousHeightPx = size.height
    }
}

@Composable
private fun ShelfGroupContent(
    group: ShelfGroup,
    selectedKey: MarketListKey?,
    listState: LazyListState,
    onKeySelected: (MarketListKey) -> Unit,
    onAssetClick: (MarketItem) -> Unit,
) {
    val tabs =
        group.items.map { MoonTabItem(id = it.key.ordinal, title = it.title) }.toImmutableList()
    val items = group.items.firstOrNull { it.key == selectedKey }?.items.orEmpty()
    val assetsState = ShelfAssetsState(
        groupName = group.name,
        selectedKey = selectedKey,
        items = items,
    )

    if (group.items.size > 1 && selectedKey != null) {
        MoonContentTabs(
            items = tabs,
            selectedId = selectedKey.ordinal,
            onSelect = { onKeySelected(MarketListKey.entries[it.id]) },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    AnimatedContent(
        targetState = assetsState,
        contentKey = { "${it.groupName}|${it.selectedKey}" },
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.scrollOnHeightGrow(listState),
        transitionSpec = { shelfAssetsTransition() },
        label = "shelfAssetsTransition",
    ) { state ->
        AssetsGrid(items = state.items, onAssetClick = onAssetClick)
    }
}

@Composable
private fun ShelfContainer(
    title: String,
    onTitleClick: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        MoonBundleTitleCell(
            title = title,
            onClick = onTitleClick,
            content = rightContent,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(shape = Shapes.medium, color = UIKit.colorScheme.background.content)
                .padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AssetsGrid(
    items: List<MarketItem>,
    onAssetClick: (MarketItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        AssetItem(
                            item = item,
                            onClick = { onAssetClick(item) },
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AssetItem(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(vertical = 8.dp),
    item: MarketItem,
    onClick: (() -> Unit)?,
) {
    val mcAsset = remember(item.asset.id) { Asset.coinFromString(item.asset.id) }
    val chainImageUrl = mcAsset?.tokenChainImageUrl()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium12)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonCutBadgedBox(
            badge = if (chainImageUrl != null) {
                { MoonItemImage(image = chainImageUrl, size = 20.dp) }
            } else {
                null
            },
            direction = BadgeDirection.EndBottom,
        ) {
            MoonAsyncImage(
                modifier = Modifier
                    .background(color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                    .size(56.dp)
                    .clip(CircleShape),
                image = item.asset.imageUrl,
                placeholder = painterResource(UIKitIcon.ic_illustration),
                error = painterResource(UIKitIcon.ic_illustration),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.asset.symbol,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.asset.verification == AssetRefSummary.Verification.trusted) {
                MoonVerificationBadge(size = 12.dp)
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
        val formattedChange = Formatter.percent(item.metrics.change24hPercent)
        Text(
            text = formattedChange,
            style = UIKit.typography.body3,
            color = formattedChange.percentDiffColor(),
            maxLines = 1,
        )
    }
}

private fun mockItem(symbol: String, change: String) =
    MarketItem(
        asset = AssetRefSummary(
            assetType = AssetType.asset,
            id = "ton::default::$symbol",
            symbol = symbol,
            name = symbol,
            decimals = 9,
            imageUrl = "",
            trustScore = 0,
            isScam = false,
            verification = AssetRefSummary.Verification.whitelist,
        ),
        metrics = MarketMetricsSummary(
            volume = "0",
            price = "0.00001",
            change24hPercent = change,
            provider = "mock",
            asOf = "",
        ),
    )

private val previewShelfGroups = listOf(
    MultichainShelfGroup(
        id = "top_movers",
        name = "Top Movers",
        groups = listOf(
            ShelfGroup(
                name = "TON",
                items = listOf(
                    ShelfConfig(
                        key = MarketListKey.top_gainers,
                        title = "Top Gainers",
                        type = ShelfType.grid,
                        source = "mock",
                        seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                        items = listOf(
                            mockItem("SHREK", "18.43"),
                            mockItem("WZP", "-1.40"),
                        ),
                    ),
                    ShelfConfig(
                        key = MarketListKey.top_losers,
                        title = "Top Losers",
                        type = ShelfType.grid,
                        source = "mock",
                        seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                        items = listOf(
                            mockItem("B3", "+22.10"),
                            mockItem("VES", "+12.50"),
                        ),
                    ),
                ),
            ),
            ShelfGroup(
                name = "Ethereum",
                items = listOf(
                    ShelfConfig(
                        key = MarketListKey.top_gainers,
                        title = "Top Gainers",
                        type = ShelfType.grid,
                        source = "mock",
                        seeAll = ShelfConfigSeeAll(enabled = false, route = AssetsTab.all),
                        items = listOf(
                            mockItem("ETH", "+5.20"),
                            mockItem("UNI", "+3.10"),
                        ),
                    ),
                ),
            ),
        ),
    ),
    MultichainShelfGroup(
        id = "most_traded",
        name = "Most Traded",
        groups = listOf(
            ShelfGroup(
                name = "TON",
                items = listOf(
                    ShelfConfig(
                        key = MarketListKey.most_traded,
                        title = "Most Traded",
                        type = ShelfType.grid,
                        source = "mock",
                        seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                        items = listOf(
                            mockItem("TRUMPUS", "-4.50"),
                            mockItem("NOOTYA", "-2.30"),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private val previewFavoriteAssets = listOf(
    mockItem("SHIB", "+1.91"),
    mockItem("USDC", "-0.01"),
    mockItem("USDT", "+1.91"),
    mockItem("DAI", "+1.91"),
)

@Preview
@Composable
private fun ShelvesScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesContent(
            isLoading = false,
            shelfGroups = previewShelfGroups,
            favoriteAssets = previewFavoriteAssets,
            shelvesError = false,
            selections = emptyMap(),
            onNetworkSelected = { _, _ -> },
            onTabSelected = { _, _ -> },
            onSelectShelf = { _, _, _ -> },
            onRefresh = { },
            onOpenSearch = { },
            onOpenSeeAll = { _, _, _ -> },
            onOpenAssetDetails = { },
            onOpenPerps = { },
            isPerpsVisible = false,
            onRemoveFavorites = { },
        )
    }
}

@Preview
@Composable
private fun ShelvesScreenLoadingPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesContent(
            isLoading = true,
            shelfGroups = null,
            favoriteAssets = emptyList(),
            shelvesError = false,
            selections = emptyMap(),
            onNetworkSelected = { _, _ -> },
            onTabSelected = { _, _ -> },
            onSelectShelf = { _, _, _ -> },
            onRefresh = { },
            onOpenSearch = { },
            onOpenSeeAll = { _, _, _ -> },
            onOpenAssetDetails = { },
            onOpenPerps = { },
            isPerpsVisible = false,
            onRemoveFavorites = { },
        )
    }
}

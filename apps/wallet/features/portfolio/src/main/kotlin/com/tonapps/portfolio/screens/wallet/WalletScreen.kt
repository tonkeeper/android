package com.tonapps.portfolio.screens.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.Banner.BannerFrom
import com.tonapps.bus.generated.Events.Banner.BannerWalletMode
import com.tonapps.bus.generated.Events.EarnFlow.EarnEntryViewFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDeviceViewFrom
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.BannerAnalytics
import com.tonapps.core.components.BannersViewer
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.deeplink.isRaffleDeeplink
import com.tonapps.core.deeplink.withPerpsBannerEntry
import com.tonapps.core.loyalty.LOYALTY_DEEPLINK
import com.tonapps.core.loyalty.rememberLoyaltyBanner
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.portfolio.LocalPortfolioIsTop
import com.tonapps.portfolio.screens.wallet.components.ActionButtons
import com.tonapps.portfolio.screens.wallet.components.AllAssetsHiddenCell
import com.tonapps.portfolio.screens.wallet.components.AssetsHeader
import com.tonapps.portfolio.screens.wallet.components.CollectiblesSection
import com.tonapps.portfolio.screens.wallet.components.EarnPositionsSection
import com.tonapps.portfolio.screens.wallet.components.EarnPositionsUnavailableSection
import com.tonapps.portfolio.screens.wallet.components.EarnSection
import com.tonapps.portfolio.screens.wallet.components.FinishSetupCard
import com.tonapps.portfolio.screens.wallet.components.LoyaltyRow
import com.tonapps.portfolio.screens.wallet.components.SectionMoreCell
import com.tonapps.portfolio.screens.wallet.components.SectionMoreImage
import com.tonapps.portfolio.screens.wallet.components.PerpsPositionsSection
import com.tonapps.portfolio.screens.wallet.components.PerpsShelfSection
import com.tonapps.portfolio.screens.wallet.components.RaffleRow
import com.tonapps.portfolio.screens.wallet.components.StakedCell
import com.tonapps.portfolio.screens.wallet.components.TotalBalance
import com.tonapps.portfolio.screens.wallet.components.WalletTopBar
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.compareByChainDisplayOrder
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonRefresh
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.moon.MoonToastHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ui.components.popup.LazyListTooltip
import ui.components.popup.dismissTooltipOnTap
import ui.components.popup.lazyListTooltipAnchor
import ui.components.popup.rememberLazyListTooltipState
import ui.components.popup.scrollToAnchor
import ui.moon.rememberMoonToastHostState
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import uikit.widget.ToastFeedback

private const val EARN_VAULTS_KEY = "earn_vaults"
private const val PERPS_SHELF_KEY = "perps_shelf"
private const val MAX_ASSETS_SHIMMER_ROWS = 7
private const val MORE_ASSETS_PREVIEW_IMAGES = 2
private val WalletBannerAnalytics = BannerAnalytics(BannerWalletMode.Multi, BannerFrom.Home)

private sealed interface WalletAssetRow {
    val fiat: BigDecimal
    val key: String
    val chain: Chain

    data class Account(
        val account: AccountWithDetails,
        val hiddenBalance: Boolean,
        override val fiat: BigDecimal,
    ) : WalletAssetRow {
        override val key: String get() = account.asset.id
        override val chain: Chain get() = account.data.chain
    }

    data class Staked(
        val item: StakedUi,
    ) : WalletAssetRow {
        override val fiat: BigDecimal get() = item.fiat
        override val key: String get() = "staked_${item.poolAddress}"
        override val chain: Chain get() = Chain.Ton.Mainnet
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    feature: WalletFeature,
    onAction: (WalletAction) -> Unit,
    onOpenAsset: (AssetEntity) -> Unit,
    onOpenStakeViewer: (poolAddress: String, poolName: String) -> Unit = { _, _ -> },
    onOpenStakeWithdraw: (poolAddress: String) -> Unit = {},
    onManageClick: () -> Unit = {},
    onCryptoClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onEnablePushClick: () -> Unit = {},
    onMigrationClick: () -> Unit = {},
    onEnableBiometryClick: () -> Unit = {},
    onOpenCollectibles: () -> Unit = {},
    onOpenNft: (NftEntity) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenRaffle: (walletId: String, raffleId: String) -> Unit = { _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    onOpenBattery: () -> Unit = {},
    onAddressClick: () -> Unit = {},
    onOpenPerps: (entryPoint: PerpsEntryPoint) -> Unit = {},
    onOpenPerpMarket: (marketIndex: Int, symbol: String, entryPoint: PerpsEntryPoint) -> Unit = { _, _, _ -> },
    onOpenEarn: (from: EarnEntryViewFrom) -> Unit = {},
    onOpenEarnVault: (network: String, vaultId: String, from: EarnEntryViewFrom) -> Unit = { _, _, _ -> },
    onOpenLedgerDevice: (walletId: String, from: LedgerDeviceViewFrom) -> Unit = { _, _ -> },
) {
    val walletState by feature.wallet.collectAsStateWorkaround()
    val tonAddress by feature.tonAddress.collectAsStateWorkaround()
    val actions by feature.actions.collectAsStateWorkaround()
    val settingsBadge by feature.settingsBadge.collectAsStateWorkaround()
    val finishSetup by feature.finishSetup.collectAsStateWorkaround()
    val pushEnabling by feature.pushEnabling.collectAsStateWorkaround()
    val totalState by feature.totalUi.collectAsStateWorkaround()
    val isTotalStale by feature.isTotalStale.collectAsStateWorkaround()
    val battery by feature.battery.collectAsStateWorkaround()
    val batteryHint by feature.batteryHint.collectAsStateWorkaround()
    val banners by feature.banners.collectAsStateWorkaround()
    val loyalty by feature.loyalty.collectAsStateWorkaround()
    val hiddenBalances by feature.hiddenBalances.collectAsStateWorkaround()
    val loyaltyBanner = rememberLoyaltyBanner()
    val shownBanners = remember(banners, loyalty.showsBanner, loyaltyBanner) {
        if (loyalty.showsBanner) {
            listOf(loyaltyBanner) + banners
        } else {
            banners
        }
    }
    val raffleRows by feature.raffleRows.collectAsStateWorkaround()
    val collectibles by feature.collectibles.collectAsStateWorkaround()
    val assetApy by feature.assetApy.collectAsStateWorkaround()
    val staked by feature.staked.collectAsStateWorkaround()
    val sections by feature.sections.collectAsStateWorkaround()
    val showsEarnTooltip by feature.showsEarnTooltip.collectAsStateWorkaround()
    val showsPerpsTooltip by feature.showsPerpsTooltip.collectAsStateWorkaround()
    val pagingItems = feature.accountsFlow.collectAsLazyPagingItems()

    val assetsLimit by feature.assetsLimit.collectAsStateWorkaround()

    val wallet = walletState ?: return

    // earn_entry_view goes once per appearance of the screen: coming back to it or to another
    // wallet counts again, scrolling a block out and back does not.
    var appearance by remember { mutableIntStateOf(0) }
    val shownEarnEntries = remember(wallet.id, appearance) { mutableSetOf<EarnEntryViewFrom>() }

    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val showsAssetsShimmer = isLoading && pagingItems.itemCount == 0 && staked.isEmpty()
    val showsTotalShimmer by remember { derivedStateOf { totalState == null } }
    val pullToRefreshState = rememberPullToRefreshState()
    val earnTooltip = rememberLazyListTooltipState(listState)
    val perpsTooltip = rememberLazyListTooltipState(listState)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val top = info.viewportStartOffset + info.beforeContentPadding
            val bottom = info.viewportEndOffset - info.afterContentPadding
            val shelf = info.visibleItemsInfo.firstOrNull { it.key == PERPS_SHELF_KEY }
            when {
                shelf == null -> PerpsShelfVisibility.Gone
                !listState.isScrollInProgress && shelf.offset >= top && shelf.offset + shelf.size <= bottom -> PerpsShelfVisibility.Full
                else -> PerpsShelfVisibility.Partial
            }
        }
            .distinctUntilChanged()
            .collect(feature::setPerpsShelfVisibility)
    }
    val scope = rememberCoroutineScope()
    var userPullRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(pagingItems.loadState.refresh) {
        if (pagingItems.loadState.refresh !is LoadState.Loading) {
            userPullRefresh = false
        }
    }

    val isTop = LocalPortfolioIsTop.current
    LifecycleResumeEffect(isTop) {
        feature.refreshPushStatus()
        if (isTop) {
            feature.refreshPerps()
            feature.setResumed(true)
            feature.onScreenShown()
            appearance++
        }
        onPauseOrDispose { feature.setResumed(false) }
    }

    val raffleEvents = AnalyticsHelper.Default.events.mysteryRaffle
    val raffleSurfaceVisible = remember(banners, raffleRows) {
        raffleRows.isNotEmpty() ||
                banners.any { it.button?.payload?.let(::isRaffleDeeplink) == true }
    }
    LaunchedEffect(raffleSurfaceVisible) {
        if (raffleSurfaceVisible) {
            raffleEvents.raffleBannerView(MysteryRaffleSource.WalletMain)
        }
    }

    val toastHost = rememberMoonToastHostState()
    val errorText = stringResource(Localization.something_went_wrong)
    val errorIconColor = UIKit.colorScheme.accent.orange
    LaunchedEffect(Unit) {
        feature.error.collect {
            toastHost.showToast(
                text = errorText,
                icon = UIKitIcon.ic_exclamationmark_triangle_16,
                iconColor = errorIconColor,
                feedback = ToastFeedback.Failure,
            )
        }
    }

    val liquidAssetIds = remember(staked) {
        staked.mapNotNull { it.liquidAssetId }.toSet()
    }

    val assetRows = remember(pagingItems.itemCount, pagingItems.itemSnapshotList, staked, liquidAssetIds) {
        val accounts = buildList {
            for (index in 0 until pagingItems.itemCount) {
                val item = pagingItems.peek(index) ?: continue
                if (item.account.asset.id in liquidAssetIds) {
                    continue
                }
                add(
                    WalletAssetRow.Account(
                        account = item.account,
                        hiddenBalance = item.hiddenBalance,
                        fiat = item.account.fiatValue,
                    )
                )
            }
        }
        val stakedRows = staked.map { WalletAssetRow.Staked(it) }
        // Equal balances, an empty wallet's zeros above all, fall back to the network order.
        (stakedRows + accounts).sortedWith(
            compareByDescending<WalletAssetRow> { it.fiat }
                .then(compareByChainDisplayOrder { it.chain })
        )
    }

    val appendEndReached = pagingItems.loadState.append.endOfPaginationReached
    LaunchedEffect(pagingItems.itemCount, assetsLimit, pagingItems.loadState.refresh, appendEndReached) {
        val lastPreviewIndex = (assetsLimit.count + MORE_ASSETS_PREVIEW_IMAGES).coerceAtMost(pagingItems.itemCount) - 1
        if (lastPreviewIndex >= 0) {
            pagingItems[lastPreviewIndex]
        }
    }

    val refreshSettled = pagingItems.loadState.refresh is LoadState.NotLoading
    val settledHasNextPage = remember { mutableStateOf(false) }
    SideEffect {
        if (refreshSettled) {
            settledHasNextPage.value = !appendEndReached
        }
    }
    val hasNextPage = if (refreshSettled) {
        !appendEndReached
    } else {
        settledHasNextPage.value
    }
    val showsPendingMoreRow = hasNextPage &&
            pagingItems.itemCount >= assetsLimit.count &&
            assetRows.size >= assetsLimit.count

    Box(modifier = Modifier.fillMaxSize()) {
        MoonScaffold(
            topBar = {
                WalletTopBar(
                    wallet = wallet,
                    onWalletClick = onWalletClick,
                    onScanClick = onScanClick,
                    onHistoryClick = onHistoryClick,
                    onSettingsClick = onSettingsClick,
                    settingsBadge = settingsBadge,
                )
            },
        ) {
            val shimmerPhase by rememberShimmerPhase(enabled = showsTotalShimmer || showsAssetsShimmer)

            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = userPullRefresh && pagingItems.loadState.refresh is LoadState.Loading,
                onRefresh = {
                    userPullRefresh = true
                    feature.refreshAssets()
                    pagingItems.refresh()
                },
                state = pullToRefreshState,
                indicator = {
                    MoonRefresh(
                        modifier = Modifier.align(Alignment.TopCenter),
                        state = pullToRefreshState,
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dismissTooltipOnTap(
                            enabled = showsEarnTooltip,
                            onDismiss = feature::dismissEarnTooltip,
                        )
                        .dismissTooltipOnTap(
                            enabled = showsPerpsTooltip,
                            onDismiss = feature::dismissPerpsTooltip,
                        ),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 80.dp,
                    ),
                    overscrollEffect = null,
                ) {
                    item(contentType = "total") {
                        when (val total = totalState) {
                            null -> TotalBalance(shimmerPhase = shimmerPhase)
                            else -> TotalBalance(
                                total = total,
                                onBalanceClick = feature::toggleHiddenBalances,
                                stale = isTotalStale,
                                battery = battery,
                                batteryHint = batteryHint,
                                address = tonAddress,
                                onBatteryClick = onOpenBattery,
                                onAddressClick = onAddressClick,
                            )
                        }
                    }
                    item(contentType = "action_buttons") {
                        ActionButtons(
                            actions = actions,
                            onClick = onAction,
                        )
                    }
                    if (shownBanners.isNotEmpty()) {
                        item(contentType = "banners") {
                            BannersViewer(
                                banners = shownBanners,
                                analytics = WalletBannerAnalytics,
                                onClick = { button ->
                                    if (isRaffleDeeplink(button.payload)) {
                                        raffleEvents.raffleBannerClick(MysteryRaffleSource.WalletMain)
                                        onOpenLink(button.payload.withRaffleSource(MysteryRaffleSource.WalletMain.key))
                                    } else {
                                        onOpenLink(button.payload.withPerpsBannerEntry())
                                    }
                                },
                                onHide = { banner -> feature.hideBanner(banner) },
                            )
                        }
                    }

                    if (loyalty.showsSection) {
                        item(contentType = "loyalty") {
                            LoyaltyRow(
                                ohms = loyalty.positiveOhms,
                                hiddenBalance = hiddenBalances,
                                onClick = { onOpenLink(LOYALTY_DEEPLINK) },
                            )
                        }
                    }

                    items(
                        count = raffleRows.size,
                        key = { index -> "raffle_" + raffleRows[index].id },
                        contentType = { "raffle" },
                    ) { index ->
                        val raffle = raffleRows[index]
                        RaffleRow(
                            raffle = raffle,
                            onClick = {
                                raffleEvents.raffleBannerClick(MysteryRaffleSource.WalletMain)
                                onOpenRaffle(wallet.id, raffle.id)
                            },
                        )
                    }

                    finishSetup?.let { setup ->
                        item(contentType = "finish_setup") {
                            FinishSetupCard(
                                state = setup,
                                pushEnabling = pushEnabling,
                                onBackupClick = onBackupClick,
                                onEnablePushClick = {
                                    feature.onEnablePushStarted()
                                    onEnablePushClick()
                                },
                                onMigrationClick = onMigrationClick,
                                onEnableBiometryClick = onEnableBiometryClick,
                                onLedgerNetworksClick = { onOpenLedgerDevice(wallet.id, LedgerDeviceViewFrom.SetupSection) },
                                onSkipClick = { feature.skipFinishSetup() },
                            )
                        }
                    }
                    when {
                        showsAssetsShimmer -> {
                            item(contentType = "assets_header") {
                                AssetsHeader(shimmerPhase = shimmerPhase)
                            }
                            val shimmerRows = assetsLimit.count.coerceAtMost(MAX_ASSETS_SHIMMER_ROWS)
                            items(shimmerRows, contentType = { "account_shimmer" }) { index ->
                                AccountCellShimmer(
                                    position = defaultBundleType(shimmerRows, index),
                                )
                            }
                        }
                        else -> {
                            val assetsEmpty = assetRows.isEmpty() && pagingItems.itemCount == 0
                            var afterAssets = false
                            sections.forEach { section ->
                                val spaced = afterAssets
                                when (section) {
                                    is WalletSection.Assets -> {
                                        afterAssets = true
                                        if (assetsEmpty) {
                                            collectibles?.let { collectiblesState ->
                                                item(contentType = "collectibles") {
                                                    CollectiblesSection(
                                                        state = collectiblesState,
                                                        onHeaderClick = onOpenCollectibles,
                                                        onSeeAllClick = onOpenCollectibles,
                                                        onNftClick = onOpenNft,
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                }
                                            }
                                            item(contentType = "assets_header") {
                                                AssetsHeader(onClick = onCryptoClick, onManageClick = onManageClick)
                                            }
                                            item(contentType = "assets_hidden") {
                                                AllAssetsHiddenCell(onClick = onManageClick)
                                            }
                                        } else {
                                            item(contentType = "assets_header") {
                                                AssetsHeader(onClick = onCryptoClick, onManageClick = onManageClick)
                                            }
                                            val loadedAssetsCount = assetRows.size
                                            val visibleAssetsCount = loadedAssetsCount.coerceAtMost(assetsLimit.count)
                                            val showMoreButton = loadedAssetsCount > assetsLimit.count ||
                                                    showsPendingMoreRow
                                            val bundleSize = visibleAssetsCount + if (showMoreButton) {
                                                1
                                            } else {
                                                0
                                            }
                                            items(
                                                count = visibleAssetsCount,
                                                key = { index -> assetRows[index].key },
                                                contentType = { index ->
                                                    when (assetRows[index]) {
                                                        is WalletAssetRow.Account -> "account"
                                                        is WalletAssetRow.Staked -> "staked"
                                                    }
                                                },
                                            ) { index ->
                                                when (val row = assetRows[index]) {
                                                    is WalletAssetRow.Account -> {
                                                        val apy = assetApy[row.account.asset.id]
                                                        AccountCell(
                                                            account = row.account,
                                                            position = defaultBundleType(bundleSize, index),
                                                            onClick = { onOpenAsset(row.account.asset) },
                                                            hiddenBalance = row.hiddenBalance,
                                                            tags = if (apy != null) {
                                                                {
                                                                    MoonLabel(
                                                                        text = "$apy ${stringResource(Localization.staking_apy)}",
                                                                        colors = MoonLabelDefault.success(),
                                                                    )
                                                                }
                                                            } else {
                                                                null
                                                            },
                                                        )
                                                    }
                                                    is WalletAssetRow.Staked -> {
                                                        StakedCell(
                                                            item = row.item,
                                                            position = defaultBundleType(bundleSize, index),
                                                            onClick = {
                                                                onOpenStakeViewer(
                                                                    row.item.poolAddress,
                                                                    row.item.poolName
                                                                )
                                                            },
                                                            onReadyWithdrawClick = {
                                                                onOpenStakeWithdraw(row.item.poolAddress)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                            if (showMoreButton) {
                                                item(contentType = "more_assets") {
                                                    val previewRows = remember(assetRows, visibleAssetsCount) {
                                                        assetRows
                                                            .drop(visibleAssetsCount)
                                                            .take(MORE_ASSETS_PREVIEW_IMAGES)
                                                    }
                                                    SectionMoreCell(
                                                        title = stringResource(Localization.more_assets),
                                                        images = previewRows.map { row ->
                                                            when (row) {
                                                                is WalletAssetRow.Account -> SectionMoreImage.Remote(
                                                                    url = row.account.asset.assetImageUrl(),
                                                                    preview = row.account.asset.symbol,
                                                                )
                                                                is WalletAssetRow.Staked -> SectionMoreImage.Local(
                                                                    res = UIKitIcon.ic_gram_with_bg,
                                                                )
                                                            }
                                                        },
                                                        limit = assetsLimit,
                                                        limitTestTag = "wallet_assets_limit",
                                                        position = defaultBundleType(bundleSize, bundleSize - 1),
                                                        onClick = onCryptoClick,
                                                        onLimitSelect = feature::selectAssetsLimit,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is WalletSection.Perps.Positions -> item(key = "perps_positions", contentType = "perps_positions") {
                                        SectionSpacer(spaced)
                                        PerpsPositionsSection(
                                            state = section,
                                            onOpenPerps = { onOpenPerps(PerpsEntryPoint.MainPositions) },
                                            onOpenPerpMarket = { marketIndex, symbol ->
                                                onOpenPerpMarket(marketIndex, symbol, PerpsEntryPoint.MainPositions)
                                            },
                                        )
                                    }
                                    is WalletSection.Perps.Shelf -> item(key = PERPS_SHELF_KEY, contentType = "perps_shelf") {
                                        SectionSpacer(spaced)
                                        PerpsShelfSection(
                                            state = section,
                                            onOpenPerps = { onOpenPerps(PerpsEntryPoint.MainCarousel) },
                                            onOpenPerpMarket = { marketIndex, symbol ->
                                                onOpenPerpMarket(marketIndex, symbol, PerpsEntryPoint.MainCarousel)
                                            },
                                            titleModifier = if (showsPerpsTooltip) {
                                                Modifier.lazyListTooltipAnchor(perpsTooltip, PERPS_SHELF_KEY)
                                            } else {
                                                Modifier
                                            },
                                        )
                                    }
                                    is WalletSection.Earn.Positions -> item(key = "earn_positions", contentType = "earn_positions") {
                                        LaunchedEffect(shownEarnEntries) {
                                            if (appearance > 0 && shownEarnEntries.add(EarnEntryViewFrom.EarnYourVaults)) {
                                                feature.onEarnPositionsShown()
                                            }
                                        }
                                        SectionSpacer(spaced)
                                        EarnPositionsSection(
                                            totalFormat = section.totalFormat,
                                            positions = section.positions,
                                            hiddenPositions = section.hiddenPositions,
                                            isStale = section.isStale,
                                            limit = section.limit,
                                            onOpenEarn = { onOpenEarn(EarnEntryViewFrom.EarnYourVaults) },
                                            onOpenPosition = { network, vaultId ->
                                                onOpenEarnVault(network, vaultId, EarnEntryViewFrom.EarnYourVaults)
                                            },
                                            onRetry = feature::retryEarn,
                                            onLimitSelect = feature::selectEarnLimit,
                                        )
                                        SectionSpacer(!spaced)
                                    }
                                    is WalletSection.Earn.Unavailable -> item(key = "earn_unavailable", contentType = "earn_unavailable") {
                                        SectionSpacer(spaced)
                                        EarnPositionsUnavailableSection(onRetry = feature::retryEarn)
                                        SectionSpacer(!spaced)
                                    }
                                    is WalletSection.Earn.Vaults -> item(key = EARN_VAULTS_KEY, contentType = "earn_vaults") {
                                        LaunchedEffect(shownEarnEntries) {
                                            if (appearance > 0 && shownEarnEntries.add(EarnEntryViewFrom.EarnFeatured)) {
                                                feature.onEarnVaultsShown()
                                            }
                                        }
                                        SectionSpacer(spaced)
                                        EarnSection(
                                            vaults = section.vaults,
                                            onOpenEarn = { onOpenEarn(EarnEntryViewFrom.EarnFeatured) },
                                            onOpenVault = { network, vaultId ->
                                                onOpenEarnVault(network, vaultId, EarnEntryViewFrom.EarnFeatured)
                                            },
                                            titleModifier = if (showsEarnTooltip) {
                                                Modifier.lazyListTooltipAnchor(earnTooltip, EARN_VAULTS_KEY)
                                            } else {
                                                Modifier
                                            },
                                        )
                                    }
                                }
                            }
                            if (!assetsEmpty) {
                                collectibles?.let { collectiblesState ->
                                    item(contentType = "collectibles") {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        CollectiblesSection(
                                            state = collectiblesState,
                                            onHeaderClick = onOpenCollectibles,
                                            onSeeAllClick = onOpenCollectibles,
                                            onNftClick = onOpenNft,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (showsEarnTooltip) {
                    LazyListTooltip(
                        state = earnTooltip,
                        itemKey = EARN_VAULTS_KEY,
                        text = stringResource(Localization.tooltip_earn),
                        badge = stringResource(Localization.badge_new),
                        backgroundColor = UIKit.colorScheme.accent.blue,
                        textColor = Color.White,
                        onClick = {
                            scope.launch {
                                try {
                                    earnTooltip.scrollToAnchor(EARN_VAULTS_KEY)
                                } finally {
                                    feature.dismissEarnTooltip()
                                }
                            }
                        },
                    )
                }
                if (showsPerpsTooltip) {
                    LazyListTooltip(
                        state = perpsTooltip,
                        itemKey = PERPS_SHELF_KEY,
                        text = stringResource(Localization.tooltip_perps),
                        badge = stringResource(Localization.badge_new),
                        backgroundColor = UIKit.colorScheme.accent.blue,
                        textColor = Color.White,
                        onClick = feature::dismissPerpsTooltip,
                    )
                }
            }
        }
        MoonToastHost(toastHost)
    }
}

@Composable
private fun SectionSpacer(visible: Boolean) {
    if (visible) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

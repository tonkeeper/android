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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.BannersViewer
import com.tonapps.core.components.isNativeTon
import com.tonapps.core.deeplink.isRaffleDeeplink
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.portfolio.screens.wallet.components.ActionButtons
import com.tonapps.portfolio.screens.wallet.components.AllAssetsHiddenCell
import com.tonapps.portfolio.screens.wallet.components.AssetsHeader
import com.tonapps.portfolio.screens.wallet.components.CollectiblesSection
import com.tonapps.portfolio.screens.wallet.components.FinishSetupCard
import com.tonapps.portfolio.screens.wallet.components.MoreAssetsCell
import com.tonapps.portfolio.screens.wallet.components.RaffleRow
import com.tonapps.portfolio.screens.wallet.components.StakedCell
import com.tonapps.portfolio.screens.wallet.components.TotalBalance
import com.tonapps.portfolio.screens.wallet.components.WalletTopBar
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonRefresh
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase

private const val PreviewAssetsLimit = 7

private sealed interface WalletAssetRow {
    val fiat: BigDecimal
    val key: String

    data class Account(
        val account: AccountWithDetails,
        override val fiat: BigDecimal,
    ) : WalletAssetRow {
        override val key: String get() = account.asset.id
    }

    data class Staked(
        val item: StakedUi,
    ) : WalletAssetRow {
        override val fiat: BigDecimal get() = item.fiat
        override val key: String get() = "staked_${item.poolAddress}"
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
    onOpenBattery: () -> Unit = {},
    onAddressClick: () -> Unit = {},
) {
    val walletState by feature.wallet.collectAsStateWorkaround()
    val tonAddress by feature.tonAddress.collectAsStateWorkaround()
    val settingsBadge by feature.settingsBadge.collectAsStateWorkaround()
    val finishSetup by feature.finishSetup.collectAsStateWorkaround()
    val pushEnabling by feature.pushEnabling.collectAsStateWorkaround()
    val totalState by feature.total.collectAsStateWorkaround()
    val battery by feature.battery.collectAsStateWorkaround()
    val banners by feature.banners.collectAsStateWorkaround()
    val raffleRows by feature.raffleRows.collectAsStateWorkaround()
    val collectibles by feature.collectibles.collectAsStateWorkaround()
    val stakingApy by feature.stakingApy.collectAsStateWorkaround()
    val staked by feature.staked.collectAsStateWorkaround()
    val pagingItems = feature.accountsFlow.collectAsLazyPagingItems()

    val wallet = walletState ?: return

    var assetsExpanded by remember(wallet.id) { mutableStateOf(false) }

    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val pullToRefreshState = rememberPullToRefreshState()
    var userPullRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(pagingItems.loadState.refresh) {
        if (pagingItems.loadState.refresh !is LoadState.Loading) {
            userPullRefresh = false
        }
    }

    LifecycleResumeEffect(Unit) {
        feature.refreshPushStatus()
        onPauseOrDispose { }
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
                if (item.asset.id in liquidAssetIds) {
                    continue
                }
                add(
                    WalletAssetRow.Account(
                        account = item,
                        fiat = item.fiatValue,
                    )
                )
            }
        }
        val stakedRows = staked.map { WalletAssetRow.Staked(it) }
        (stakedRows + accounts).sortedByDescending { it.fiat }
    }

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
        val shimmerPhase by rememberShimmerPhase()

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
                modifier = Modifier.fillMaxSize(),
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
                            battery = battery,
                            address = tonAddress,
                            onBatteryClick = onOpenBattery,
                            onAddressClick = onAddressClick,
                        )
                    }
                }
                item(contentType = "action_buttons") {
                    ActionButtons(
                        actions = feature.actions,
                        onClick = onAction,
                    )
                }
                if (banners.isNotEmpty()) {
                    item(contentType = "banners") {
                        BannersViewer(
                            banners = banners,
                            onClick = { button ->
                                if (isRaffleDeeplink(button.payload)) {
                                    raffleEvents.raffleBannerClick(MysteryRaffleSource.WalletMain)
                                    onOpenLink(button.payload.withRaffleSource(MysteryRaffleSource.WalletMain.key))
                                } else {
                                    onOpenLink(button.payload)
                                }
                            },
                            onHide = { banner -> feature.hideBanner(banner) },
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
                            onSkipClick = { feature.skipFinishSetup() },
                        )
                    }
                }
                when {
                    isLoading && pagingItems.itemCount == 0 && staked.isEmpty() -> {
                        item(contentType = "assets_header") {
                            AssetsHeader(shimmerPhase = shimmerPhase)
                        }
                        items(7, contentType = { "account_shimmer" }) { index ->
                            AccountCellShimmer(
                                position = defaultBundleType(7, index),
                            )
                        }
                    }
                    assetRows.isEmpty() && pagingItems.itemCount == 0 -> {
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
                    }
                    else -> {
                        item(contentType = "assets_header") {
                            AssetsHeader(onClick = onCryptoClick, onManageClick = onManageClick)
                        }
                        val loadedAssetsCount = assetRows.size
                        val hasMoreAssets = loadedAssetsCount > PreviewAssetsLimit
                        val showMoreButton = hasMoreAssets && !assetsExpanded
                        val visibleAssetsCount = when {
                            assetsExpanded -> loadedAssetsCount
                            hasMoreAssets -> PreviewAssetsLimit - 1
                            else -> loadedAssetsCount
                        }
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
                                    val apy = stakingApy
                                    AccountCell(
                                        account = row.account,
                                        position = defaultBundleType(bundleSize, index),
                                        onClick = { onOpenAsset(row.account.asset) },
                                        tags = if (apy != null && row.account.asset.isNativeTon()) {
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
                                            onOpenStakeViewer(row.item.poolAddress, row.item.poolName)
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
                                val previewAssets = remember(assetRows, visibleAssetsCount) {
                                    assetRows
                                        .drop(visibleAssetsCount)
                                        .take(2)
                                        .mapNotNull { (it as? WalletAssetRow.Account)?.account?.asset }
                                }
                                MoreAssetsCell(
                                    assets = previewAssets,
                                    position = defaultBundleType(bundleSize, bundleSize - 1),
                                    onClick = { assetsExpanded = true },
                                )
                            }
                        }
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
    }
        MoonToastHost(toastHost)
    }
}

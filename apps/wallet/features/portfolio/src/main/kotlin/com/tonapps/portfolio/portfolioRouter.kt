package com.tonapps.portfolio

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.tonapps.bus.generated.Events.EarnFlow.EarnEntryViewFrom
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.portfolio.screens.wallet.WalletAction
import com.tonapps.portfolio.screens.wallet.WalletFeature
import com.tonapps.portfolio.screens.wallet.WalletScreen
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import ui.moon.MoonNav

@Serializable
sealed interface PortfolioRoutes : NavKey {
    @Serializable
    data object Wallet : PortfolioRoutes
}

@Composable
fun PortfolioRouter(
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenAsset: (AssetEntity) -> Unit,
    onOpenAccountsManage: () -> Unit,
    onOpenPortfolioSearch: () -> Unit,
    onOpenWalletsList: () -> Unit,
    onOpenSwaps: () -> Unit,
    onOpenStake: () -> Unit,
    onOpenStakeViewer: (poolAddress: String, poolName: String) -> Unit,
    onOpenStakeWithdraw: (poolAddress: String) -> Unit,
    onOpenScanner: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackupClick: () -> Unit = {},
    onEnablePushClick: () -> Unit = {},
    onMigrationClick: () -> Unit = {},
    onEnableBiometryClick: () -> Unit = {},
    onOpenCollectibles: () -> Unit = {},
    onOpenNft: (NftEntity) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenRaffle: (walletId: String, raffleId: String) -> Unit = { _, _ -> },
    scrollToTop: Flow<Unit> = emptyFlow(),
    onOpenBattery: () -> Unit = {},
    onOpenReceivingAddress: () -> Unit = {},
    onOpenPerps: (entryPoint: PerpsEntryPoint) -> Unit = {},
    onOpenPerpMarket: (marketIndex: Int, symbol: String, entryPoint: PerpsEntryPoint) -> Unit = { _, _, _ -> },
    onOpenEarn: (from: EarnEntryViewFrom) -> Unit = {},
    onOpenEarnVault: (network: String, vaultId: String, from: EarnEntryViewFrom) -> Unit = { _, _, _ -> },
) {
    val backStack = rememberNavBackStack(PortfolioRoutes.Wallet)

    val walletListState = rememberLazyListState()
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            walletListState.animateScrollToItem(0)
        }
    }

    MoonNav(backStack = backStack) { key ->
        when (key) {
            is PortfolioRoutes.Wallet -> NavEntry(key) {
                val feature = koinViewModel<WalletFeature>()
                WalletScreen(
                    feature = feature,
                    onAction = { action ->
                        when (action) {
                            WalletAction.Deposit -> onOpenDeposit()
                            WalletAction.Send -> onOpenWithdraw()
                            WalletAction.Swap -> onOpenSwaps()
                            WalletAction.Stake -> onOpenStake()
                        }
                    },
                    onOpenAsset = onOpenAsset,
                    onOpenStakeViewer = onOpenStakeViewer,
                    onOpenStakeWithdraw = onOpenStakeWithdraw,
                    onManageClick = onOpenAccountsManage,
                    onCryptoClick = onOpenPortfolioSearch,
                    onWalletClick = onOpenWalletsList,
                    onScanClick = onOpenScanner,
                    onHistoryClick = onHistoryClick,
                    onSettingsClick = onSettingsClick,
                    onBackupClick = onBackupClick,
                    onEnablePushClick = onEnablePushClick,
                    onMigrationClick = onMigrationClick,
                    onEnableBiometryClick = onEnableBiometryClick,
                    onOpenCollectibles = onOpenCollectibles,
                    onOpenNft = onOpenNft,
                    onOpenLink = onOpenLink,
                    onOpenRaffle = onOpenRaffle,
                    listState = walletListState,
                    onOpenBattery = onOpenBattery,
                    onAddressClick = onOpenReceivingAddress,
                    onOpenPerps = onOpenPerps,
                    onOpenPerpMarket = onOpenPerpMarket,
                    onOpenEarn = onOpenEarn,
                    onOpenEarnVault = onOpenEarnVault,
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}

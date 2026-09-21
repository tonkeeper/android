package com.tonapps.portfolio

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.tonapps.portfolio.screens.wallet.WalletAction
import com.tonapps.portfolio.screens.wallet.WalletFeature
import com.tonapps.portfolio.screens.wallet.WalletScreen
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.multichain.asset.AssetEntity
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
    onOpenBattery: () -> Unit = {},
    onOpenReceivingAddress: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(PortfolioRoutes.Wallet)

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
                    onOpenBattery = onOpenBattery,
                    onAddressClick = onOpenReceivingAddress,
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}

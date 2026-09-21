package com.tonapps.portfolio

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import uikit.extensions.activity

class PortfolioFragment : ComposableFragment() {
    override val fragmentName: String = "PortfolioFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            PortfolioRouter(
                onOpenDeposit = { delegate?.onOpenDeposit() },
                onOpenWithdraw = { delegate?.onOpenWithdraw() },
                onOpenAsset = { asset ->
                    delegate?.onOpenAssetDetails(
                        assetId = asset.id,
                        previewName = asset.name,
                        previewImageUrl = asset.imageUrl,
                        from = AssetScreenFrom.WalletScreen
                    )
                },
                onOpenAccountsManage = { delegate?.onOpenAccountsManage() },
                onOpenPortfolioSearch = { delegate?.onOpenPortfolioSearch() },
                onOpenWalletsList = { delegate?.onOpenWalletsList() },
                onOpenSwaps = { delegate?.onOpenSwap() },
                onOpenStake = { delegate?.onOpenStake() },
                onOpenStakeViewer = { poolAddress, poolName ->
                    delegate?.onOpenStakeViewer(poolAddress, poolName)
                },
                onOpenStakeWithdraw = { poolAddress ->
                    delegate?.onOpenStakeWithdraw(poolAddress)
                },
                onOpenScanner = { delegate?.onOpenScanner() },
                onSettingsClick = { delegate?.onOpenSettings() },
                onHistoryClick = { delegate?.onOpenHistory() },
                onBackupClick = { delegate?.onOpenBackup() },
                onEnablePushClick = { delegate?.onEnablePush() },
                onMigrationClick = { delegate?.onOpenMigration() },
                onEnableBiometryClick = { delegate?.onEnableBiometry() },
                onOpenCollectibles = { delegate?.onOpenCollectibles() },
                onOpenNft = { nft -> delegate?.onOpenNft(nft) },
                onOpenLink = { url -> delegate?.onOpenLink(url) },
                onOpenRaffle = { walletId, raffleId -> delegate?.onOpenRaffle(walletId, raffleId) },
                onOpenBattery = { delegate?.onOpenBattery(from = BatteryNativeFrom.Wallet) },
                onOpenReceivingAddress = { delegate?.onOpenReceivingAddress() },
            )
        }
    }

    companion object {
        fun newInstance(): PortfolioFragment = PortfolioFragment()
    }
}

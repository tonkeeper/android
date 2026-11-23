package com.tonapps.core.navigation

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.generated.Events
import com.tonapps.bus.screenrender.ScreenRenderTrigger
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.data.collectibles.entities.NftEntity

interface NavigationDelegate {
    // [walletId]: the wallet the calling screen shows; null when it shows the selected one.
    fun onOpenDeposit(from: Events.DepositFlow.DepositFlowFrom, walletId: String?)
    fun onOpenReceivingAddress()
    fun onOpenWithdraw(from: Events.WithdrawFlow.WithdrawFlowFrom)
    fun onOpenAssetDetails(
        assetId: String,
        previewName: String,
        previewImageUrl: String,
        from: Events.AssetScreen.AssetScreenFrom,
    )
    fun onOpenPerpMarket(marketIndex: Int, symbol: String, entryPoint: Events.Perps.PerpsEntryPoint)

    fun onOpenPerps(entryPoint: Events.Perps.PerpsEntryPoint)

    fun onOpenPerpsBasics()

    fun onOpenEarn(from: Events.EarnFlow.EarnEntryViewFrom)

    fun onOpenEarnVault(network: String, vaultId: String, from: Events.EarnFlow.EarnEntryViewFrom)

    fun onOpenAccountsManage()

    fun onOpenPortfolioSearch(
        initialSort: PortfolioSearchSort? = null,
        initialNetwork: Network.Type? = null,
        initialAssetType: PortfolioSearchAssetType? = null,
    )

    fun onOpenWalletsList(perpsWalletsOnly: Boolean = false)

    fun onOpenWalletLabelEdit(walletId: String)

    fun onOpenAddWallet()

    fun onOpenSwap()

    fun onOpenConfirm(request: ConfirmRequest)

    fun onOpenStake()

    fun onOpenStakeViewer(poolAddress: String, poolName: String)

    fun onOpenStakeWithdraw(poolAddress: String)

    fun onOpenSettings()

    /** [trigger] is what opened it, for screen render analytics; by default the app opened it after an action. */
    fun onOpenHistory(trigger: ScreenRenderTrigger = ScreenRenderTrigger.Auto)

    fun onOpenPerpsHistory(awaitsFundingActivity: Boolean = false)

    fun onOpenAssetHistory(assetId: String)

    fun onOpenScanner()

    fun onOpenCollectibles()

    fun onOpenNft(nft: NftEntity)

    fun onOpenNft(address: String)

    fun onOpenBackup() {
    }

    fun onOpenMigration() {
    }

    fun onEnableBiometry() {
    }

    fun onEnablePush() {
    }

    fun onOpenLedgerImport(payload: String, deviceName: String, migrationFrom: Events.LedgerFlow.LedgerMigrationStartFrom?) {
    }

    fun onOpenLedgerDevice(walletId: String, from: Events.LedgerFlow.LedgerDeviceViewFrom) {
    }

    fun onOpenLink(url: String)

    fun onOpenRaffle(walletId: String, raffleId: String)

    fun onOpenBatteryFromWallet()

    fun onOpenBattery(walletId: String? = null, from: Events.BatteryNative.BatteryNativeFrom)

    fun onOpenTopUp(request: ConfirmRequest, assetId: String)

    /**
     * Returns whether the link was recognised and handed to the app, not whether a screen opened:
     * the flow it reaches may still decline it (a watch-only wallet, a legacy wallet, no wallet).
     */
    fun onProcessDeeplink(value: String, fromQR: Boolean = false): Boolean

    fun navigateTaskBack()
}
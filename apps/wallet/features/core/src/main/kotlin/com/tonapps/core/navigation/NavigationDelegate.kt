package com.tonapps.core.navigation

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.generated.Events
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.data.collectibles.entities.NftEntity

interface NavigationDelegate {
    fun onOpenDeposit()
    fun onOpenReceivingAddress()
    fun onOpenWithdraw()
    fun onOpenAssetDetails(
        assetId: String,
        previewName: String,
        previewImageUrl: String,
        from: Events.AssetScreen.AssetScreenFrom,
    )
    fun onOpenPerpMarket(marketIndex: Int, symbol: String)

    fun onOpenAccountsManage()

    fun onOpenPortfolioSearch(
        initialSort: PortfolioSearchSort? = null,
        initialNetwork: Network.Type? = null,
    )

    fun onOpenWalletsList()

    fun onOpenWalletLabelEdit(walletId: String)

    fun onOpenAddWallet()

    fun onOpenSwap()

    fun onOpenConfirm(request: ConfirmRequest)

    fun onOpenStake()

    fun onOpenStakeViewer(poolAddress: String, poolName: String)

    fun onOpenStakeWithdraw(poolAddress: String)

    fun onOpenSettings()

    fun onOpenHistory()

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

    fun onOpenLink(url: String)

    fun onOpenRaffle(walletId: String, raffleId: String)

    fun onOpenBattery(walletId: String? = null, from: Events.BatteryNative.BatteryNativeFrom)

    fun onProcessDeeplink(value: String, fromQR: Boolean = false)

    fun navigateTaskBack()
}
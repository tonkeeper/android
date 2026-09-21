package com.tonapps.portfolio.analytics

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletInterface
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletMode
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletSource

object PortfolioAnalytics {

    private val events: Events.WalletOpen
        get() = AnalyticsHelper.Default.events.walletOpen

    fun open(wallet: WalletEntity) {
        events.walletOpen(
            walletMode = walletMode(wallet),
            walletSource = walletSource(wallet),
            walletInterface = walletInterface(wallet),
        )
    }

    private fun walletMode(wallet: WalletEntity): WalletOpenWalletMode {
        return if (wallet.type == WalletType.Multichain) {
            WalletOpenWalletMode.Multi
        } else {
            WalletOpenWalletMode.Single
        }
    }

    private fun walletSource(wallet: WalletEntity): WalletOpenWalletSource {
        return when {
            wallet.isLedger -> WalletOpenWalletSource.Ledger
            wallet.isKeystone -> WalletOpenWalletSource.Keystone
            wallet.signer -> WalletOpenWalletSource.Signer
            wallet.isWatchOnly -> WalletOpenWalletSource.Watchonly
            else -> WalletOpenWalletSource.Mnemonic
        }
    }

    // Multichain wallets span several interfaces, so the schema omits wallet_interface there.
    private fun walletInterface(wallet: WalletEntity): WalletOpenWalletInterface? {
        if (wallet.type == WalletType.Multichain) {
            return null
        }
        return when (wallet.version) {
            WalletVersion.V5R1 -> WalletOpenWalletInterface.V5R1
            WalletVersion.V5BETA -> WalletOpenWalletInterface.V5Beta
            WalletVersion.V4R2 -> WalletOpenWalletInterface.V4R2
            WalletVersion.V4R1 -> WalletOpenWalletInterface.V4R1
            WalletVersion.V3R2 -> WalletOpenWalletInterface.V3R2
            WalletVersion.V3R1 -> WalletOpenWalletInterface.V3R1
            WalletVersion.UNKNOWN -> null
        }
    }
}

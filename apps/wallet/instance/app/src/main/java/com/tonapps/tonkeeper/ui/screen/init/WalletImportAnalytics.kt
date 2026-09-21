package com.tonapps.tonkeeper.ui.screen.init

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository

// Import failures that happen before InitScreen opens (Ledger pairing, Signer/Keystone QR scanning)
// have no InitViewModel to report them, so they go through here instead.
class WalletImportAnalytics(
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val analytics: AnalyticsHelper,
) {

    suspend fun trackError(
        walletSource: WalletFlowWalletSource,
        errorType: String,
        errorMessage: String? = null,
    ) {
        analytics.events.walletFlow.walletImportError(
            // Ledger, Signer and Keystone accounts are always single-chain, matching
            // InitViewModel.importedWalletMode.
            walletMode = WalletFlowWalletMode.Single,
            walletSource = walletSource,
            from = if (unifiedAccountRepository.hasAnyWallet()) {
                WalletFlowFrom.Main
            } else {
                WalletFlowFrom.Onboarding
            },
            errorType = errorType,
            errorCode = null,
            errorMessage = errorMessage,
        )
    }
}

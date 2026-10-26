package com.tonapps.tonkeeper.ui.screen.init

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.log.L
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.CancellationException

// Import events that happen before InitScreen opens (Ledger pairing, Signer/Keystone QR scanning)
// have no InitViewModel to report them, so they go through here instead.
class WalletImportAnalytics(
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val analytics: AnalyticsHelper,
) {

    suspend fun trackError(
        walletSource: WalletFlowWalletSource,
        errorType: String,
        errorMessage: String? = null,
        ledgerModel: WalletFlowLedgerModel? = null,
    ) {
        analytics.events.walletFlow.walletImportError(
            // Ledger, Signer and Keystone accounts are always single-chain, matching
            // InitViewModel.importedWalletMode.
            walletMode = WalletFlowWalletMode.Single,
            walletSource = walletSource,
            from = from(),
            errorType = errorType,
            errorCode = null,
            errorMessage = errorMessage,
            ledgerModel = ledgerModel,
        )
    }

    suspend fun trackSignerScanned() {
        analytics.events.walletFlow.walletImportSignerScanned(
            walletMode = WalletFlowWalletMode.Single,
            walletSource = WalletFlowWalletSource.Signer,
            from = from(),
        )
    }

    suspend fun trackKeystoneScanned() {
        analytics.events.walletFlow.walletImportKeystoneScanned(
            walletMode = WalletFlowWalletMode.Single,
            walletSource = WalletFlowWalletSource.Keystone,
            from = from(),
        )
    }

    suspend fun trackLedgerBluetoothConnected(ledgerModel: WalletFlowLedgerModel) {
        analytics.events.walletFlow.walletImportLedgerBluetoothConnected(
            walletMode = WalletFlowWalletMode.Single,
            walletSource = WalletFlowWalletSource.Ledger,
            from = from(),
            ledgerModel = ledgerModel,
        )
    }

    suspend fun trackLedgerAppOpened(ledgerModel: WalletFlowLedgerModel?) {
        analytics.events.walletFlow.walletImportLedgerAppOpened(
            walletMode = WalletFlowWalletMode.Single,
            walletSource = WalletFlowWalletSource.Ledger,
            from = from(),
            ledgerModel = ledgerModel,
        )
    }

    private suspend fun from(): WalletFlowFrom {
        val hasAnyWallet = try {
            unifiedAccountRepository.hasAnyWallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            true
        }
        return if (hasAnyWallet) {
            WalletFlowFrom.Main
        } else {
            WalletFlowFrom.Onboarding
        }
    }
}

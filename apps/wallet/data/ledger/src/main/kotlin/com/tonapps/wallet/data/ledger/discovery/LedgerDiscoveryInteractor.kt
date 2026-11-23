package com.tonapps.wallet.data.ledger.discovery

import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.wallet.data.ledger.LedgerChainApp

interface LedgerDiscoveryInteractor {

    suspend fun discover(
        device: LedgerDevice,
        indices: List<Int>,
        apps: List<LedgerChainApp>,
        purpose: LedgerPurpose = LedgerPurpose.ImportAccounts,
        walletId: String? = null,
        expectedTonAddress: String? = null,
        onAppState: (LedgerChainApp, LedgerAppDiscoveryState) -> Unit = { _, _ -> },
    ): LedgerDiscoveryResult
}

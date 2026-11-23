package com.tonapps.core.flags

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LedgerMigrationTooltipInteractor {

    suspend fun shouldShow(): Boolean = withContext(Dispatchers.IO) {
        WalletFeature.LedgerMultichain.isEnabled && TooltipManager.shouldShowToday(KEY, PLACEMENT)
    }

    fun onSeen() {
        TooltipManager.markShownToday(KEY, PLACEMENT)
    }

    fun onDismissedByUser() {
        TooltipManager.setState(KEY, TooltipState.SHOWN)
    }
}

private val KEY = WalletTooltipKey.LEDGER_MULTICHAIN_MIGRATION
private const val PLACEMENT = "main"

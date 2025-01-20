package com.tonapps.tonkeeper.ui.screen.ledger.steps

sealed class LedgerStep {
    data object Connect: LedgerStep()
    data object OpenTonApp: LedgerStep()
    data object ConfirmTx: LedgerStep()
    data object Done: LedgerStep()
}



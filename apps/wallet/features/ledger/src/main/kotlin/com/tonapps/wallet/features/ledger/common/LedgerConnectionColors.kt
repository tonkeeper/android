package com.tonapps.wallet.features.ledger.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionLevel
import ui.theme.UIKit

@Composable
fun LedgerConnectionLevel.statusColor(): Color = when (this) {
    LedgerConnectionLevel.Connected -> UIKit.colorScheme.accent.green
    LedgerConnectionLevel.Nearby -> UIKit.colorScheme.accent.blue
    LedgerConnectionLevel.Away -> UIKit.colorScheme.icon.tertiary
}

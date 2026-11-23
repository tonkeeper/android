package com.tonapps.bus.walletimport

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingEntry
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingExitReason
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource

class WalletImportPairingSessions(
    private val walletFlow: () -> Events.WalletFlow = { AnalyticsHelper.Default.events.walletFlow },
) {

    private var current: WalletImportPairingSession? = null

    @Synchronized
    fun start(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        entry: WalletFlowPairingEntry = WalletFlowPairingEntry.Menu,
        hasPairingScreen: Boolean = true,
    ): WalletImportPairingSession {
        current?.exit(WalletFlowPairingExitReason.Other)
        val session = WalletImportPairingSession(
            walletMode = walletMode,
            walletSource = walletSource,
            from = from,
            entry = entry,
            hasPairingScreen = hasPairingScreen,
            walletFlow = walletFlow,
        )
        current = session
        session.start()
        return session
    }

    @Synchronized
    fun active(walletSource: WalletFlowWalletSource): WalletImportPairingSession? {
        return current?.takeIf { it.isActive && it.walletSource == walletSource }
    }
}

package com.tonapps.wallet.data.ledger.connection

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerPurpose

sealed interface LedgerConnectionStatus {

    data class Busy(val purpose: LedgerPurpose, val progress: LedgerProgress) : LedgerConnectionStatus

    data class Connected(val app: LedgerAppId?, val isUsb: Boolean = false) : LedgerConnectionStatus

    data object Nearby : LedgerConnectionStatus

    data object UsbConnected : LedgerConnectionStatus

    data object UsbDisconnected : LedgerConnectionStatus

    data object Searching : LedgerConnectionStatus

    data object NotFound : LedgerConnectionStatus

    data object BluetoothOff : LedgerConnectionStatus

    data object NoPermission : LedgerConnectionStatus

    data object LocationOff : LedgerConnectionStatus

    data object Unsupported : LedgerConnectionStatus
}

enum class LedgerScanPolicy {
    Detailed,
}

data class LedgerActivity(
    val id: Long,
    val deviceId: String,
    val purpose: LedgerPurpose,
    val startedAt: Long,
    val outcome: LedgerActivityOutcome,
)

sealed interface LedgerActivityOutcome {

    data class InProgress(val progress: LedgerProgress) : LedgerActivityOutcome

    data object Done : LedgerActivityOutcome

    data class Failed(val failure: LedgerFailure) : LedgerActivityOutcome

    data object Interrupted : LedgerActivityOutcome
}

enum class LedgerConnectionLevel {
    Connected,
    Nearby,
    Away,
}

val LedgerConnectionStatus.level: LedgerConnectionLevel
    get() = when (this) {
        is LedgerConnectionStatus.Busy -> when (progress) {
            LedgerProgress.WaitingUsb -> LedgerConnectionLevel.Away
            LedgerProgress.Connecting,
            LedgerProgress.Reconnecting,
            LedgerProgress.Pairing,
            LedgerProgress.UsbAccess -> LedgerConnectionLevel.Nearby
            else -> LedgerConnectionLevel.Connected
        }
        is LedgerConnectionStatus.Connected,
        LedgerConnectionStatus.UsbConnected -> LedgerConnectionLevel.Connected
        LedgerConnectionStatus.Nearby -> LedgerConnectionLevel.Nearby
        LedgerConnectionStatus.UsbDisconnected,
        LedgerConnectionStatus.Searching,
        LedgerConnectionStatus.NotFound,
        LedgerConnectionStatus.BluetoothOff,
        LedgerConnectionStatus.NoPermission,
        LedgerConnectionStatus.LocationOff,
        LedgerConnectionStatus.Unsupported -> LedgerConnectionLevel.Away
    }

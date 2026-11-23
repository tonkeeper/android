package com.tonapps.wallet.features.ledger.progress

import androidx.compose.runtime.Immutable
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerLinkKind

@Immutable
internal data class LedgerProgressView(
    val state: LedgerOperationState,
    val lastProgress: LedgerProgress? = null,
    val app: LedgerAppId? = null,
    val lastConfirm: LedgerProgress.Confirm? = null,
    val linkKind: LedgerLinkKind = LedgerLinkKind.Bluetooth,
) {

    val isUsb: Boolean
        get() = linkKind == LedgerLinkKind.Usb || lastProgress == LedgerProgress.WaitingUsb || lastProgress == LedgerProgress.UsbAccess

    fun pluggedInAfterConnectFailure(previous: LedgerProgressView): Boolean =
        previous.linkKind == LedgerLinkKind.Bluetooth &&
            linkKind == LedgerLinkKind.Usb &&
            state is LedgerOperationState.Failed &&
            state.canRetry &&
            stage == LedgerStage.Connect

    fun next(state: LedgerOperationState): LedgerProgressView {
        val base = if (state.request == this.state.request) { this } else { LedgerProgressView(state) }
        val progress = (state as? LedgerOperationState.Active)?.progress ?: return base.copy(state = state)
        return base.copy(
            state = state,
            lastProgress = progress,
            app = progress.app ?: base.app,
            lastConfirm = progress as? LedgerProgress.Confirm ?: base.lastConfirm,
        )
    }
}

internal val LedgerOperationState.request: LedgerRequest?
    get() = when (this) {
        is LedgerOperationState.Active -> request
        is LedgerOperationState.Failed -> request
        is LedgerOperationState.Done -> request
        LedgerOperationState.Idle -> null
    }

private val LedgerProgress.app: LedgerAppId?
    get() = when (this) {
        is LedgerProgress.OpeningApp -> app
        is LedgerProgress.ClosingApp -> target
        is LedgerProgress.Working -> app
        is LedgerProgress.Confirm -> app
        is LedgerProgress.VerifyAddress -> app
        LedgerProgress.Connecting,
        LedgerProgress.Pairing,
        LedgerProgress.WaitingUsb,
        LedgerProgress.UsbAccess,
        LedgerProgress.FinishPendingRequest,
        LedgerProgress.WaitingUnlock,
        LedgerProgress.Reconnecting -> null
    }

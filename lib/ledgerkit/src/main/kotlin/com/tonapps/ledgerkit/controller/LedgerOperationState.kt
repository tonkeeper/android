package com.tonapps.ledgerkit.controller

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.transport.LedgerLinkKind

sealed interface LedgerOperationState {

    data object Idle : LedgerOperationState

    data class Active(
        val request: LedgerRequest,
        val progress: LedgerProgress,
    ) : LedgerOperationState

    data class Failed(
        val request: LedgerRequest,
        val failure: LedgerFailure,
        val canRetry: Boolean,
    ) : LedgerOperationState

    data class Done(val request: LedgerRequest) : LedgerOperationState
}

data class LedgerOpenLink(
    val deviceId: String,
    val app: LedgerAppInfo?,
    val kind: LedgerLinkKind = LedgerLinkKind.Bluetooth,
)

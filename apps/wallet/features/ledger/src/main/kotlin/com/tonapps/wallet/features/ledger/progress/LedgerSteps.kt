package com.tonapps.wallet.features.ledger.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.wallet.features.ledger.common.displayName
import com.tonapps.wallet.features.ledger.common.ledgerFailureText
import com.tonapps.wallet.localization.Localization

internal enum class LedgerStepStatus {
    Pending,
    Current,
    Done,
    Failed,
}

@Immutable
internal class LedgerStepItem(val label: String, val status: LedgerStepStatus, val hint: String?)

internal enum class LedgerStage {
    Connect,
    App,
    Confirm,
}

internal val LedgerProgressView.stage: LedgerStage?
    get() = when (state) {
        is LedgerOperationState.Active -> state.progress.stage
        is LedgerOperationState.Failed -> lastProgress?.stage ?: LedgerStage.Connect
        is LedgerOperationState.Done,
        LedgerOperationState.Idle -> null
    }

private val LedgerProgress.stage: LedgerStage
    get() = when (this) {
        LedgerProgress.Connecting,
        LedgerProgress.Pairing,
        LedgerProgress.WaitingUsb,
        LedgerProgress.UsbAccess -> LedgerStage.Connect
        LedgerProgress.FinishPendingRequest,
        LedgerProgress.WaitingUnlock,
        is LedgerProgress.OpeningApp,
        is LedgerProgress.ClosingApp,
        LedgerProgress.Reconnecting,
        is LedgerProgress.Working -> LedgerStage.App
        is LedgerProgress.Confirm,
        is LedgerProgress.VerifyAddress -> LedgerStage.Confirm
    }

@Composable
internal fun ledgerSteps(view: LedgerProgressView): List<LedgerStepItem> {
    val stage = view.stage
    val isDone = view.state is LedgerOperationState.Done
    val isFailed = view.state is LedgerOperationState.Failed
    val hint = when (val state = view.state) {
        is LedgerOperationState.Active -> stepHint(state.progress, view.isUsb)
        is LedgerOperationState.Failed -> ledgerFailureText(state.failure)
        else -> null
    }

    fun status(rowStage: LedgerStage): LedgerStepStatus = when {
        isDone || stage == null -> LedgerStepStatus.Done
        rowStage < stage -> LedgerStepStatus.Done
        rowStage > stage -> LedgerStepStatus.Pending
        isFailed -> LedgerStepStatus.Failed
        else -> LedgerStepStatus.Current
    }

    fun item(label: String, status: LedgerStepStatus) =
        LedgerStepItem(label, status, hint.takeIf { status == LedgerStepStatus.Current || status == LedgerStepStatus.Failed })

    val appLabel = view.app?.let { stringResource(Localization.ledger_mc_step_open_app, it.displayName) }
        ?: stringResource(Localization.ledger_mc_step_open_any_app)
    val confirm = view.lastConfirm
    val purpose = view.state.request?.purpose
    return buildList {
        add(item(stringResource(if (view.isUsb) { Localization.ledger_usb_connect } else { Localization.ledger_connect }), status(LedgerStage.Connect)))
        add(item(appLabel, status(LedgerStage.App)))
        val confirmStatus = status(LedgerStage.Confirm)
        if (confirm != null && confirm.kind == LedgerConfirmKind.Transaction && confirm.totalSteps > 1) {
            for (step in 1..confirm.totalSteps) {
                val stepStatus = when {
                    confirmStatus != LedgerStepStatus.Current && confirmStatus != LedgerStepStatus.Failed -> confirmStatus
                    step < confirm.step -> LedgerStepStatus.Done
                    step > confirm.step -> LedgerStepStatus.Pending
                    else -> confirmStatus
                }
                add(item(stringResource(Localization.ledger_confirm_tx_step, step), stepStatus))
            }
        } else {
            confirmLabel(confirm?.kind, purpose, view.lastProgress is LedgerProgress.VerifyAddress)?.let { add(item(it, confirmStatus)) }
        }
    }
}

@Composable
private fun confirmLabel(kind: LedgerConfirmKind?, purpose: LedgerPurpose?, isVerifyAddress: Boolean): String? {
    if (isVerifyAddress) {
        return stringResource(Localization.ledger_mc_progress_verify_address)
    }
    val label = when (kind) {
        LedgerConfirmKind.Transaction -> Localization.ledger_mc_progress_confirm_transaction
        LedgerConfirmKind.Message -> Localization.ledger_mc_progress_confirm_message
        LedgerConfirmKind.TypedData -> Localization.ledger_mc_progress_confirm_typed_data
        LedgerConfirmKind.Proof -> Localization.ledger_mc_progress_confirm_proof
        LedgerConfirmKind.Wallet -> Localization.ledger_mc_progress_confirm_wallet
        null -> when (purpose) {
            LedgerPurpose.SignTransaction -> Localization.ledger_mc_progress_confirm_transaction
            LedgerPurpose.SignMessage -> Localization.ledger_mc_progress_confirm_message
            LedgerPurpose.SignProof -> Localization.ledger_mc_progress_confirm_proof
            LedgerPurpose.CreateWallet -> Localization.ledger_mc_progress_confirm_wallet
            LedgerPurpose.VerifyAddress -> Localization.ledger_mc_progress_verify_address
            LedgerPurpose.Connect,
            LedgerPurpose.ImportAccounts,
            LedgerPurpose.AddAccounts,
            null -> null
        }
    }
    return label?.let { stringResource(it) }
}

@Composable
private fun stepHint(progress: LedgerProgress, isUsb: Boolean): String? = when (progress) {
    LedgerProgress.Connecting -> if (isUsb) {
        stringResource(Localization.ledger_mc_progress_usb_hint)
    } else {
        stringResource(Localization.ledger_mc_progress_connecting_hint)
    }
    LedgerProgress.Pairing -> stringResource(Localization.ledger_mc_progress_pairing_hint)
    LedgerProgress.WaitingUsb -> stringResource(Localization.ledger_mc_progress_usb_hint)
    LedgerProgress.UsbAccess -> stringResource(Localization.ledger_mc_progress_usb_access_hint)
    LedgerProgress.FinishPendingRequest -> stringResource(Localization.ledger_mc_progress_pending_hint)
    LedgerProgress.WaitingUnlock -> stringResource(Localization.ledger_mc_progress_unlock)
    is LedgerProgress.OpeningApp -> stringResource(Localization.ledger_mc_progress_open_app_hint)
    is LedgerProgress.ClosingApp -> stringResource(Localization.ledger_mc_progress_close_app, progress.openAppName)
    LedgerProgress.Reconnecting -> stringResource(Localization.ledger_mc_progress_reconnecting)
    is LedgerProgress.Working -> null
    is LedgerProgress.Confirm -> when {
        progress.kind == LedgerConfirmKind.Wallet && progress.totalSteps > 1 ->
            stringResource(Localization.ledger_mc_progress_confirm_account_step, progress.step, progress.totalSteps)
        progress.kind == LedgerConfirmKind.Wallet -> stringResource(Localization.ledger_mc_progress_confirm_wallet_hint)
        progress.kind != LedgerConfirmKind.Transaction && progress.totalSteps > 1 ->
            stringResource(Localization.ledger_mc_progress_confirm_step, progress.step, progress.totalSteps)
        else -> null
    }
    is LedgerProgress.VerifyAddress -> stringResource(Localization.ledger_mc_progress_verify_address_hint)
}

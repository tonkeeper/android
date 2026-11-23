package com.tonapps.wallet.features.ledger.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.wallet.localization.Localization

internal val LedgerAppId.displayName: String
    get() = openName

internal class LedgerProgressText(val title: String, val subtitle: String?)

@Composable
internal fun ledgerProgressText(progress: LedgerProgress): LedgerProgressText = when (progress) {
    LedgerProgress.Connecting -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_connecting),
        stringResource(Localization.ledger_mc_progress_connecting_hint),
    )
    LedgerProgress.Pairing -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_pairing),
        stringResource(Localization.ledger_mc_progress_pairing_hint),
    )
    LedgerProgress.WaitingUsb -> LedgerProgressText(
        stringResource(Localization.ledger_usb_connect),
        stringResource(Localization.ledger_mc_progress_usb_hint),
    )
    LedgerProgress.UsbAccess -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_usb_access),
        stringResource(Localization.ledger_mc_progress_usb_access_hint),
    )
    LedgerProgress.FinishPendingRequest -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_pending),
        stringResource(Localization.ledger_mc_progress_pending_hint),
    )
    LedgerProgress.WaitingUnlock -> LedgerProgressText(stringResource(Localization.ledger_mc_progress_unlock), null)
    is LedgerProgress.OpeningApp -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_open_app, progress.app.displayName),
        stringResource(Localization.ledger_mc_progress_open_app_hint),
    )
    is LedgerProgress.ClosingApp -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_close_app, progress.openAppName),
        null,
    )
    LedgerProgress.Reconnecting -> LedgerProgressText(stringResource(Localization.ledger_mc_progress_reconnecting), null)
    is LedgerProgress.Working -> LedgerProgressText(stringResource(Localization.ledger_mc_progress_working), null)
    is LedgerProgress.Confirm -> LedgerProgressText(
        title = when (progress.kind) {
            LedgerConfirmKind.Transaction -> stringResource(Localization.ledger_mc_progress_confirm_transaction)
            LedgerConfirmKind.Message -> stringResource(Localization.ledger_mc_progress_confirm_message)
            LedgerConfirmKind.TypedData -> stringResource(Localization.ledger_mc_progress_confirm_typed_data)
            LedgerConfirmKind.Proof -> stringResource(Localization.ledger_mc_progress_confirm_proof)
            LedgerConfirmKind.Wallet -> stringResource(Localization.ledger_mc_progress_confirm_wallet)
        },
        subtitle = when {
            progress.kind == LedgerConfirmKind.Wallet && progress.totalSteps > 1 ->
                stringResource(Localization.ledger_mc_progress_confirm_account_step, progress.step, progress.totalSteps)
            progress.kind == LedgerConfirmKind.Wallet -> stringResource(Localization.ledger_mc_progress_confirm_wallet_hint)
            progress.totalSteps > 1 -> stringResource(Localization.ledger_mc_progress_confirm_step, progress.step, progress.totalSteps)
            else -> null
        },
    )
    is LedgerProgress.VerifyAddress -> LedgerProgressText(
        stringResource(Localization.ledger_mc_progress_verify_address),
        stringResource(Localization.ledger_mc_progress_verify_address_hint),
    )
}

@Composable
internal fun ledgerFailureText(failure: LedgerFailure): String {
    val context = LocalContext.current
    return remember(context, failure) { ledgerFailureMessage(context, failure) }
}

internal fun ledgerFailureMessage(context: Context, failure: LedgerFailure): String = when (failure) {
    LedgerFailure.Cancelled -> context.getString(Localization.ledger_mc_error_refused)
    LedgerFailure.BluetoothUnavailable -> context.getString(Localization.ledger_mc_error_bluetooth_unavailable)
    LedgerFailure.BluetoothOff -> context.getString(Localization.ledger_mc_error_bluetooth_off)
    LedgerFailure.NoPermission -> context.getString(Localization.ledger_mc_error_permission)
    LedgerFailure.DeviceNotFound -> context.getString(Localization.ledger_mc_error_not_found)
    LedgerFailure.UsbNotConnected -> context.getString(Localization.ledger_mc_error_usb_not_connected)
    LedgerFailure.UsbAccessDenied -> context.getString(Localization.ledger_usb_permission_error)
    LedgerFailure.ScanUnavailable -> context.getString(Localization.ledger_mc_error_scan_unavailable)
    LedgerFailure.PairingRefused -> context.getString(Localization.ledger_mc_error_pairing)
    LedgerFailure.PairingLost -> context.getString(Localization.ledger_mc_error_pairing_lost)
    LedgerFailure.UnsupportedDevice -> context.getString(Localization.ledger_mc_error_unsupported_device)
    LedgerFailure.ConnectionLost -> context.getString(Localization.ledger_mc_error_connection_lost)
    LedgerFailure.Busy -> context.getString(Localization.ledger_mc_error_busy)
    LedgerFailure.DeviceLocked -> context.getString(Localization.ledger_mc_error_locked)
    LedgerFailure.RequestExpired -> context.getString(Localization.ledger_mc_error_expired)
    is LedgerFailure.Timeout -> context.getString(Localization.ledger_mc_error_timeout)
    is LedgerFailure.AppNotInstalled -> context.getString(Localization.ledger_mc_error_app_not_installed, failure.app.displayName)
    is LedgerFailure.WrongAppOpen -> context.getString(Localization.ledger_mc_error_wrong_app, failure.expected.displayName)
    is LedgerFailure.UserRefused -> context.getString(Localization.ledger_mc_error_refused)
    is LedgerFailure.SettingRequired -> settingMessage(context, failure)
    is LedgerFailure.AppUpdateRequired -> context.getString(
        Localization.ledger_mc_error_update,
        failure.app.displayName,
        failure.requiredVersion,
    )
    is LedgerFailure.InvalidPayload -> context.getString(Localization.ledger_mc_error_invalid)
    is LedgerFailure.AddressMismatch -> context.getString(Localization.ledger_mc_error_mismatch)
    is LedgerFailure.Unsupported -> context.getString(Localization.ledger_mc_error_unsupported)
    is LedgerFailure.DeviceError -> context.getString(Localization.ledger_mc_error_device, "0x%04x".format(failure.statusWord))
    is LedgerFailure.Unexpected -> context.getString(Localization.ledger_mc_error_unexpected)
}

private fun settingMessage(context: Context, failure: LedgerFailure.SettingRequired): String = when (failure.setting) {
    LedgerAppSetting.BlindSigning -> context.getString(Localization.ledger_mc_error_blind_signing, failure.app.displayName)
    LedgerAppSetting.RawMessages -> context.getString(Localization.ledger_mc_error_raw_messages, failure.app.displayName)
    LedgerAppSetting.DataAllowed -> context.getString(Localization.ledger_mc_error_data_allowed)
    LedgerAppSetting.CustomContracts -> context.getString(Localization.ledger_mc_error_custom_contracts)
    LedgerAppSetting.SignByHash -> context.getString(Localization.ledger_mc_error_blind_signing, failure.app.displayName)
}

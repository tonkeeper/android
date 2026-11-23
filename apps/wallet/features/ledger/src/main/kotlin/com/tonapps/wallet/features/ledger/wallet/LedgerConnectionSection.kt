package com.tonapps.wallet.features.ledger.wallet

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.wallet.data.ledger.connection.LedgerActivity
import com.tonapps.wallet.data.ledger.connection.LedgerActivityOutcome
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionLevel
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionStatus
import com.tonapps.wallet.data.ledger.connection.level
import com.tonapps.wallet.features.ledger.common.LedgerSystemAction
import com.tonapps.wallet.features.ledger.common.LedgerSystemActions
import com.tonapps.wallet.features.ledger.common.displayName
import com.tonapps.wallet.features.ledger.common.ledgerFailureText
import com.tonapps.wallet.features.ledger.common.ledgerProgressText
import com.tonapps.wallet.features.ledger.common.statusColor
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLoader
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.theme.UIKit
import java.text.DateFormat

@Composable
internal fun LedgerConnectionCell(
    status: LedgerConnectionStatus?,
    onCheck: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val action = connectionAction(status)
    val onAction: (() -> Unit)? = when (action) {
        ConnectionAction.Check -> onCheck
        ConnectionAction.Cancel -> onCancel
        is ConnectionAction.System -> { { LedgerSystemActions.perform(context, action.action) } }
        ConnectionAction.Progress, null -> null
    }
    MoonBundleCell(position = MoonBundlePosition.default(1, 0)) {
        TextCell(
            title = connectionTitle(status),
            subtitle = connectionSubtitle(status),
            maxLinesSubtitle = 3,
            image = { LedgerStatusDot(status?.level ?: LedgerConnectionLevel.Away) },
            content = {
                when (action) {
                    ConnectionAction.Progress -> MoonLoader(modifier = Modifier.size(20.dp))
                    ConnectionAction.Check -> ActionText(Localization.ledger_mc_connection_check)
                    ConnectionAction.Cancel -> ActionText(Localization.cancel)
                    is ConnectionAction.System -> ActionText(action.label)
                    null -> Unit
                }
            },
            onClick = onAction,
        )
    }
}

@Composable
private fun ActionText(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = UIKit.typography.label2,
        color = UIKit.colorScheme.accent.blue,
    )
}

@Composable
internal fun LedgerStatusDot(level: LedgerConnectionLevel, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(level.statusColor(), CircleShape),
        )
    }
}

@Composable
internal fun LedgerActivityCell(entry: LedgerActivity, position: MoonBundlePosition) {
    val context = LocalContext.current
    val time = remember(entry.startedAt, context) {
        DateUtils.formatSameDayTime(entry.startedAt, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
    }
    MoonBundleCell(position = position) {
        TextCell(
            title = stringResource(entry.purpose.label),
            subtitle = activityOutcomeText(entry.outcome),
            subtitleColor = if (entry.outcome is LedgerActivityOutcome.Failed) {
                UIKit.colorScheme.accent.red
            } else {
                UIKit.colorScheme.text.secondary
            },
            maxLinesSubtitle = 2,
            content = {
                Text(
                    text = time,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                )
            },
        )
    }
}

@Composable
private fun activityOutcomeText(outcome: LedgerActivityOutcome): String = when (outcome) {
    is LedgerActivityOutcome.InProgress -> ledgerProgressText(outcome.progress).title
    LedgerActivityOutcome.Done -> stringResource(Localization.ledger_mc_activity_done)
    is LedgerActivityOutcome.Failed -> ledgerFailureText(outcome.failure)
    LedgerActivityOutcome.Interrupted -> stringResource(Localization.ledger_mc_activity_interrupted)
}

@Composable
private fun connectionTitle(status: LedgerConnectionStatus?): String = when (status) {
    null, LedgerConnectionStatus.Searching -> stringResource(Localization.ledger_mc_connection_searching)
    is LedgerConnectionStatus.Busy -> stringResource(Localization.ledger_mc_connection_busy)
    is LedgerConnectionStatus.Connected -> stringResource(if (status.isUsb) { Localization.ledger_mc_connection_usb } else { Localization.ledger_mc_connection_connected })
    LedgerConnectionStatus.Nearby -> stringResource(Localization.ledger_mc_connection_nearby)
    LedgerConnectionStatus.UsbConnected -> stringResource(Localization.ledger_mc_connection_usb)
    LedgerConnectionStatus.UsbDisconnected -> stringResource(Localization.ledger_mc_connection_usb_unplugged)
    LedgerConnectionStatus.NotFound -> stringResource(Localization.ledger_mc_connection_not_found)
    LedgerConnectionStatus.BluetoothOff -> stringResource(Localization.ledger_mc_connection_bluetooth_off)
    LedgerConnectionStatus.NoPermission -> stringResource(Localization.ledger_mc_connection_no_permission)
    LedgerConnectionStatus.LocationOff -> stringResource(Localization.ledger_mc_connection_location_off)
    LedgerConnectionStatus.Unsupported -> stringResource(Localization.ledger_mc_connection_unsupported)
}

@Composable
private fun connectionSubtitle(status: LedgerConnectionStatus?): String? = when (status) {
    null, LedgerConnectionStatus.Searching -> stringResource(Localization.ledger_mc_connection_searching_hint)
    is LedgerConnectionStatus.Busy -> ledgerProgressText(status.progress).title
    is LedgerConnectionStatus.Connected -> connectedSubtitle(status.app)
    LedgerConnectionStatus.Nearby,
    LedgerConnectionStatus.UsbConnected -> stringResource(Localization.ledger_mc_connection_nearby_hint)
    LedgerConnectionStatus.UsbDisconnected -> stringResource(Localization.ledger_usb_connect)
    LedgerConnectionStatus.NotFound -> stringResource(Localization.ledger_mc_connection_not_found_hint)
    LedgerConnectionStatus.BluetoothOff -> stringResource(Localization.ledger_mc_connection_bluetooth_off_hint)
    LedgerConnectionStatus.NoPermission -> stringResource(Localization.ledger_mc_connection_no_permission_hint)
    LedgerConnectionStatus.LocationOff -> stringResource(Localization.ledger_mc_connection_location_off_hint)
    LedgerConnectionStatus.Unsupported -> null
}

@Composable
private fun connectedSubtitle(app: LedgerAppId?): String = if (app != null) {
    stringResource(Localization.ledger_mc_connection_connected_app, app.displayName)
} else {
    stringResource(Localization.ledger_mc_connection_connected_dashboard)
}

private sealed interface ConnectionAction {

    data object Check : ConnectionAction

    data object Cancel : ConnectionAction

    data object Progress : ConnectionAction

    data class System(val action: LedgerSystemAction, @StringRes val label: Int) : ConnectionAction
}

private fun connectionAction(status: LedgerConnectionStatus?): ConnectionAction? = when (status) {
    null -> null
    is LedgerConnectionStatus.Busy -> if (status.purpose == LedgerPurpose.Connect) {
        ConnectionAction.Cancel
    } else {
        ConnectionAction.Progress
    }
    is LedgerConnectionStatus.Connected,
    LedgerConnectionStatus.Nearby,
    LedgerConnectionStatus.UsbConnected,
    LedgerConnectionStatus.UsbDisconnected,
    LedgerConnectionStatus.Searching,
    LedgerConnectionStatus.NotFound -> ConnectionAction.Check
    LedgerConnectionStatus.BluetoothOff -> ConnectionAction.System(LedgerSystemAction.EnableBluetooth, Localization.ledger_mc_connection_turn_on)
    LedgerConnectionStatus.NoPermission -> ConnectionAction.System(LedgerSystemAction.OpenAppSettings, Localization.ledger_mc_connection_allow)
    LedgerConnectionStatus.LocationOff -> ConnectionAction.System(LedgerSystemAction.EnableLocation, Localization.ledger_mc_connection_turn_on)
    LedgerConnectionStatus.Unsupported -> null
}

private val LedgerPurpose.label: Int
    get() = when (this) {
        LedgerPurpose.Connect -> Localization.ledger_mc_activity_connect
        LedgerPurpose.ImportAccounts -> Localization.ledger_mc_activity_import
        LedgerPurpose.AddAccounts -> Localization.ledger_mc_activity_add_accounts
        LedgerPurpose.VerifyAddress -> Localization.ledger_mc_activity_verify
        LedgerPurpose.SignTransaction -> Localization.ledger_mc_activity_sign_transaction
        LedgerPurpose.SignMessage -> Localization.ledger_mc_activity_sign_message
        LedgerPurpose.SignProof -> Localization.ledger_mc_activity_sign_proof
        LedgerPurpose.CreateWallet -> Localization.ledger_mc_activity_create_wallet
    }

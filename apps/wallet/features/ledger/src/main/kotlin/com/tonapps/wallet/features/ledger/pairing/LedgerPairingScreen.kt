package com.tonapps.wallet.features.ledger.pairing

import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.usb.LedgerUsbDevice
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.features.ledger.common.LedgerDeviceHeader
import com.tonapps.wallet.features.ledger.common.LedgerMessage
import com.tonapps.wallet.features.ledger.common.LedgerSystemAction
import com.tonapps.wallet.features.ledger.common.LedgerSystemActions
import com.tonapps.wallet.features.ledger.common.displayText
import com.tonapps.wallet.features.ledger.common.ledgerFailureText
import com.tonapps.wallet.features.ledger.common.ledgerIntroDisplayText
import com.tonapps.wallet.features.ledger.common.ledgerProgressText
import com.tonapps.wallet.features.ledger.common.systemAction
import com.tonapps.wallet.features.ledger.progress.label
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonCheckbox
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLoader
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight
import ui.painterResource
import ui.preview.ThemedPreview
import ui.shortAddress
import ui.theme.UIKit

@Composable
internal fun LedgerPairingScreen(
    feature: LedgerPairingFeature,
    onClose: () -> Unit,
) {
    val state by feature.state.collectAsStateWithLifecycle()
    val operation by feature.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onSystemAction = remember(context) { { action: LedgerSystemAction -> LedgerSystemActions.perform(context, action) } }
    LedgerPairingScreenInternal(
        state = state,
        operation = operation,
        actions = feature,
        onClose = onClose,
        onSystemAction = onSystemAction,
    )
}

@Composable
private fun LedgerPairingScreenInternal(
    state: LedgerPairingState,
    operation: LedgerOperationState,
    actions: LedgerPairingActions,
    onClose: () -> Unit,
    onSystemAction: (LedgerSystemAction) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        actions.onPermissionsResult(result.values.all { it })
    }
    val onRequestPermissions: (List<String>) -> Unit = { permissions -> permissionLauncher.launch(permissions.toTypedArray()) }
    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = stepTitle(state),
        onClose = onClose,
        onBack = actions::back.takeIf { state.step != LedgerPairingStep.Connect },
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ledger_pairing_step",
        ) { step ->
            when (step) {
                LedgerPairingStep.Connect -> ConnectStep(state, actions, onSystemAction, onRequestPermissions, onClose)
                LedgerPairingStep.Discovery -> DiscoveryStep(state, operation, actions, onSystemAction)
                LedgerPairingStep.Accounts -> AccountsStep(state, actions)
            }
        }
    }
}

@Composable
private fun stepTitle(state: LedgerPairingState): String = when (state.step) {
    LedgerPairingStep.Connect -> ""
    LedgerPairingStep.Discovery -> if (state.isRepair) {
        stringResource(Localization.ledger_mc_pair_again)
    } else {
        stringResource(Localization.ledger_mc_discovery_title)
    }
    LedgerPairingStep.Accounts -> stringResource(Localization.ledger_mc_accounts_title)
}

@Composable
private fun BluetoothReadinessButton(
    state: LedgerPairingState,
    onSystemAction: (LedgerSystemAction) -> Unit,
    onRequestPermissions: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    when (val readiness = state.readiness) {
        LedgerBleReadiness.Unsupported -> MoonAccentButton(
            modifier = modifier,
            text = stringResource(Localization.close),
            buttonColors = ButtonColorsSecondary,
            size = ButtonSizeLarge,
            onClick = onClose,
        )
        is LedgerBleReadiness.MissingPermissions -> if (state.isPermissionDenied) {
            MoonAccentButton(
                modifier = modifier,
                text = stringResource(Localization.ledger_mc_open_settings),
                size = ButtonSizeLarge,
                onClick = { onSystemAction(LedgerSystemAction.OpenAppSettings) },
            )
        } else {
            MoonAccentButton(
                modifier = modifier,
                text = stringResource(Localization.ledger_mc_allow_access),
                size = ButtonSizeLarge,
                onClick = { onRequestPermissions(readiness.permissions) },
            )
        }
        LedgerBleReadiness.BluetoothOff -> MoonAccentButton(
            modifier = modifier,
            text = stringResource(Localization.ledger_mc_turn_on_bluetooth),
            size = ButtonSizeLarge,
            onClick = { onSystemAction(LedgerSystemAction.EnableBluetooth) },
        )
        LedgerBleReadiness.LocationOff -> MoonAccentButton(
            modifier = modifier,
            text = stringResource(Localization.ledger_mc_turn_on_location),
            size = ButtonSizeLarge,
            onClick = { onSystemAction(LedgerSystemAction.EnableLocation) },
        )
        LedgerBleReadiness.Ready -> Unit
    }
}

@Composable
private fun readinessNotice(state: LedgerPairingState): String? = when (state.readiness) {
    LedgerBleReadiness.Ready -> null
    LedgerBleReadiness.Unsupported -> stringResource(Localization.ledger_mc_bluetooth_unsupported)
    LedgerBleReadiness.BluetoothOff -> stringResource(Localization.ledger_mc_bluetooth_off)
    LedgerBleReadiness.LocationOff -> stringResource(Localization.ledger_mc_location_off)
    is LedgerBleReadiness.MissingPermissions -> {
        val needsLocation = Manifest.permission.ACCESS_FINE_LOCATION in (state.readiness as LedgerBleReadiness.MissingPermissions).permissions
        when {
            state.isPermissionDenied && needsLocation -> stringResource(Localization.ledger_mc_permission_denied_location)
            state.isPermissionDenied -> stringResource(Localization.ledger_mc_permission_denied)
            needsLocation -> stringResource(Localization.ledger_mc_permission_description_location)
            else -> stringResource(Localization.ledger_mc_permission_description)
        }
    }
}

@Composable
private fun ConnectStep(
    state: LedgerPairingState,
    actions: LedgerPairingActions,
    onSystemAction: (LedgerSystemAction) -> Unit,
    onRequestPermissions: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val failure = state.scanFailure
    val notReady = state.readiness != LedgerBleReadiness.Ready
    val hasUsb = state.usbDevices.isNotEmpty()
    val isSearching = !notReady && failure == null && state.devices.isEmpty() && !hasUsb
    var showHelp by remember { mutableStateOf(false) }
    LaunchedEffect(isSearching) {
        showHelp = false
        if (isSearching) {
            delay(SEARCH_HELP_DELAY_MS)
            showHelp = true
        }
    }
    StepWithBottomBar(
        bottomBar = when {
            notReady && hasUsb -> null
            notReady -> {
                { BluetoothReadinessButton(state, onSystemAction, onRequestPermissions, onClose) }
            }
            failure != null -> {
                {
                    FailureButtons(
                        failure = failure,
                        canRetry = true,
                        onRetry = actions::retryScan,
                        onSystemAction = onSystemAction,
                    )
                }
            }
            else -> null
        },
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            LedgerDeviceHeader(displayText = ledgerIntroDisplayText(state.readiness, hasUsbDevice = hasUsb))
            Spacer(modifier = Modifier.height(24.dp))
            when {
                state.isRepair -> LedgerMessage(
                    title = stringResource(Localization.ledger_mc_pair_again),
                    subtitle = stringResource(Localization.ledger_mc_pair_again_hint),
                )
                state.isMigration -> LedgerMessage(
                    title = stringResource(Localization.ledger_mc_add_networks),
                    subtitle = state.migration?.let { stringResource(Localization.ledger_mc_migrate_description, it.name) },
                )
                else -> LedgerMessage(
                    title = stringResource(Localization.ledger_mc_intro_title),
                    subtitle = stringResource(Localization.ledger_mc_intro_description),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            val notice = readinessNotice(state)
            if (notice != null) {
                NoticeCell(text = notice, isError = !hasUsb && (state.readiness !is LedgerBleReadiness.MissingPermissions || state.isPermissionDenied))
            } else if (failure != null) {
                NoticeCell(text = ledgerFailureText(failure), isError = true)
            } else {
                SearchingHeader(showHelp = showHelp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        val total = state.usbDevices.size + state.devices.size
        itemsIndexed(state.usbDevices, key = { _, device -> USB_KEY_PREFIX + device.name }) { index, device ->
            MoonBundleCell(position = MoonBundlePosition.default(total, index)) {
                UsbDeviceCell(device = device, onClick = { actions.selectUsbDevice(device) })
            }
        }
        itemsIndexed(state.devices, key = { _, device -> device.address }) { index, device ->
            MoonBundleCell(position = MoonBundlePosition.default(total, state.usbDevices.size + index)) {
                DeviceCell(device = device, onClick = { actions.selectDevice(device) })
            }
        }
    }
}

@Composable
private fun SearchingHeader(showHelp: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoonLoader(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(Localization.ledger_mc_searching),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
            )
        }
        if (showHelp) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Localization.ledger_mc_searching_help),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val SEARCH_HELP_DELAY_MS = 15_000L
private const val MESSAGE_RESERVED_LINES = 2
private const val USB_KEY_PREFIX = "usb:"

@Composable
private fun DeviceCell(device: LedgerBleDevice, onClick: () -> Unit) {
    TextCell(
        title = device.name,
        subtitle = device.model.productName,
        image = {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_ledger_device_28),
                color = UIKit.colorScheme.accent.blue,
            )
        },
        content = { MoonChevronRight() },
        onClick = onClick,
    )
}

@Composable
private fun UsbDeviceCell(device: LedgerUsbDevice, onClick: () -> Unit) {
    TextCell(
        title = device.displayName,
        subtitle = stringResource(Localization.ledger_mc_connection_usb),
        image = {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_ledger_device_28),
                color = UIKit.colorScheme.accent.blue,
            )
        },
        content = { MoonChevronRight() },
        onClick = onClick,
    )
}

@Composable
private fun DiscoveryStep(
    state: LedgerPairingState,
    operation: LedgerOperationState,
    actions: LedgerPairingActions,
    onSystemAction: (LedgerSystemAction) -> Unit,
) {
    StepWithBottomBar(
        bottomBar = {
            if (operation is LedgerOperationState.Failed) {
                FailureButtons(
                    failure = operation.failure,
                    canRetry = operation.canRetry,
                    onRetry = actions::retryOperation,
                    onSystemAction = onSystemAction,
                    onCancel = actions::back,
                )
            } else {
                MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Localization.cancel),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsSecondary,
                    onClick = actions::back,
                )
            }
        },
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            LedgerDeviceHeader(displayText = operation.displayText)
            Spacer(modifier = Modifier.height(24.dp))
            OperationMessage(operation)
            val deviceName = state.device?.name
            if (!deviceName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MoonLabel(text = deviceName)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (!state.isRepair) {
            itemsIndexed(state.apps, key = { _, row -> row.app.name }) { index, row ->
                MoonBundleCell(position = MoonBundlePosition.default(state.apps.size, index)) {
                    AppCell(app = row.app, appState = row.state)
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MoonItemSubtitle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    text = stringResource(Localization.ledger_mc_discovery_description),
                    textAlign = TextAlign.Center,
                    maxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}

@Composable
private fun OperationMessage(operation: LedgerOperationState) {
    when (operation) {
        is LedgerOperationState.Active -> {
            val text = ledgerProgressText(operation.progress)
            LedgerMessage(title = text.title, subtitle = text.subtitle, reservedLines = MESSAGE_RESERVED_LINES)
        }
        is LedgerOperationState.Failed -> LedgerMessage(
            title = stringResource(Localization.ledger_mc_error_title),
            subtitle = ledgerFailureText(operation.failure),
            isError = true,
            reservedLines = MESSAGE_RESERVED_LINES,
        )
        is LedgerOperationState.Done -> LedgerMessage(
            title = stringResource(Localization.ledger_mc_progress_done),
            subtitle = null,
            reservedLines = MESSAGE_RESERVED_LINES,
        )
        LedgerOperationState.Idle -> {
            val text = ledgerProgressText(LedgerProgress.Connecting)
            LedgerMessage(title = text.title, subtitle = text.subtitle, reservedLines = MESSAGE_RESERVED_LINES)
        }
    }
}

@Composable
private fun AppCell(app: LedgerChainApp, appState: LedgerAppDiscoveryState) {
    TextCell(
        title = stringResource(app.label),
        subtitle = stringResource(appState.label),
        subtitleColor = if (appState is LedgerAppDiscoveryState.Skipped) {
            UIKit.colorScheme.accent.orange
        } else {
            UIKit.colorScheme.text.secondary
        },
        maxLinesTitle = 2,
        content = {
            when (appState) {
                LedgerAppDiscoveryState.InProgress -> MoonLoader(modifier = Modifier.size(20.dp))
                LedgerAppDiscoveryState.Done -> LedgerStatusBadgeSmall(done = true)
                is LedgerAppDiscoveryState.Skipped -> LedgerStatusBadgeSmall(done = false)
                LedgerAppDiscoveryState.Pending -> Unit
            }
        },
    )
}

@Composable
private fun LedgerStatusBadgeSmall(done: Boolean) {
    MoonItemIcon(
        painter = painterResource(if (done) { UIKitIcon.ic_done_bold_16 } else { UIKitIcon.ic_exclamationmark_circle_16 }),
        color = if (done) { UIKit.colorScheme.accent.green } else { UIKit.colorScheme.accent.orange },
    )
}

@Composable
private fun AccountsStep(state: LedgerPairingState, actions: LedgerPairingActions) {
    StepWithBottomBar(
        bottomBar = {
            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(if (state.selected.size == 1) { Localization.add_wallet } else { Localization.ledger_mc_add_wallets }),
                size = ButtonSizeLarge,
                enabled = state.selected.isNotEmpty(),
                loading = state.isImporting,
                onClick = actions::importSelected,
            )
        },
    ) {
        item {
            MoonItemSubtitle(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                text = stringResource(Localization.ledger_mc_accounts_description),
                textAlign = TextAlign.Center,
                maxLines = Int.MAX_VALUE,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val rows = state.accounts
        itemsIndexed(rows, key = { _, row -> row.index }) { index, row ->
            MoonBundleCell(position = MoonBundlePosition.default(rows.size, index)) {
                AccountCell(row = row, isSelected = row.index in state.selected, onClick = { actions.toggle(row.index) })
            }
        }
        if (state.canLoadMore) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MoonBundleCell {
                    TextCell(
                        title = stringResource(Localization.ledger_mc_show_more),
                        titleColor = UIKit.colorScheme.accent.blue,
                        onClick = actions::loadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCell(row: LedgerAccountRow, isSelected: Boolean, onClick: () -> Unit) {
    TextCell(
        title = stringResource(Localization.ledger_mc_account_title, row.index + 1),
        tags = {
            if (row.isAlreadyAdded) {
                MoonLabel(text = stringResource(Localization.ledger_mc_account_added))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!row.isAlreadyAdded) {
                MoonCheckbox(checked = isSelected, onCheckedChange = { onClick() })
            }
        },
        description = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.addresses.forEach { AppAddressRow(it) }
            }
        },
        onClick = onClick.takeUnless { row.isAlreadyAdded },
    )
}

@Composable
private fun AppAddressRow(entry: LedgerAppAddress) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(entry.app.shortLabel),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.address.shortAddress,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoticeCell(text: String, isError: Boolean) {
    MoonBundleCell {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            text = text,
            style = UIKit.typography.body2,
            color = if (isError) { UIKit.colorScheme.accent.orange } else { UIKit.colorScheme.text.secondary },
        )
    }
}

@Composable
private fun RowScope.FailureButtons(
    failure: LedgerFailure,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onSystemAction: (LedgerSystemAction) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val action = failure.systemAction
    if (onCancel != null && (action == null || !canRetry)) {
        MoonAccentButton(
            modifier = Modifier.weight(1f),
            text = stringResource(Localization.cancel),
            size = ButtonSizeLarge,
            buttonColors = ButtonColorsSecondary,
            onClick = onCancel,
        )
    }
    if (action != null) {
        MoonAccentButton(
            modifier = Modifier.weight(1f),
            text = stringResource(action.label),
            size = ButtonSizeLarge,
            buttonColors = if (canRetry) { ButtonColorsSecondary } else { ButtonColorsPrimary },
            onClick = { onSystemAction(action) },
        )
    }
    if (canRetry) {
        MoonAccentButton(
            modifier = Modifier.weight(1f),
            text = stringResource(Localization.try_again),
            size = ButtonSizeLarge,
            onClick = onRetry,
        )
    }
}

@Composable
private fun StepWithBottomBar(
    bottomBar: (@Composable RowScope.() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    val bottomOverlayHeight = if (bottomBar != null) { moonBottomBarHeight() } else { 0.dp }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomOverlayHeight + 16.dp),
            content = content,
        )
        if (bottomBar != null) {
            MoonBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                content = bottomBar,
            )
        }
    }
}

private val LedgerChainApp.label: Int
    get() = when (this) {
        LedgerChainApp.Ton -> Localization.ledger_mc_app_ton
        LedgerChainApp.Ethereum -> Localization.ledger_mc_app_ethereum
        LedgerChainApp.Tron -> Localization.ledger_mc_app_tron
        LedgerChainApp.Bitcoin -> Localization.ledger_mc_app_bitcoin
        LedgerChainApp.Solana -> Localization.ledger_mc_app_solana
    }

private val LedgerChainApp.shortLabel: Int
    get() = when (this) {
        LedgerChainApp.Ton -> Localization.ledger_mc_app_ton
        LedgerChainApp.Ethereum -> Localization.ledger_mc_app_ethereum_short
        LedgerChainApp.Tron -> Localization.ledger_mc_app_tron
        LedgerChainApp.Bitcoin -> Localization.ledger_mc_app_bitcoin
        LedgerChainApp.Solana -> Localization.ledger_mc_app_solana
    }

private val LedgerAppDiscoveryState.label: Int
    get() = when (this) {
        LedgerAppDiscoveryState.Pending -> Localization.ledger_mc_step_pending
        LedgerAppDiscoveryState.InProgress -> Localization.ledger_mc_step_in_progress
        LedgerAppDiscoveryState.Done -> Localization.ledger_mc_step_done
        is LedgerAppDiscoveryState.Skipped -> when (reason) {
            is LedgerFailure.AppNotInstalled -> Localization.ledger_mc_step_not_installed
            is LedgerFailure.AppUpdateRequired -> Localization.ledger_mc_step_update
            else -> Localization.ledger_mc_step_declined
        }
    }

@Preview
@Composable
private fun LedgerPairingIntroPreview() {
    ThemedPreview {
        LedgerPairingScreenInternal(
            state = LedgerPairingState(isRepair = false, readiness = LedgerBleReadiness.MissingPermissions(emptyList())),
            operation = LedgerOperationState.Idle,
            actions = object : LedgerPairingActions {},
            onClose = {},
            onSystemAction = {},
        )
    }
}

@Preview
@Composable
private fun LedgerPairingDiscoveryPreview() {
    ThemedPreview {
        LedgerPairingScreenInternal(
            state = LedgerPairingState(
                isRepair = false,
                step = LedgerPairingStep.Discovery,
                device = LedgerDevice("00:11:22:33:44:55", "Stax 1A2B", LedgerModel.Stax),
                apps = listOf(
                    LedgerAppRow(LedgerChainApp.Ton, LedgerAppDiscoveryState.Done),
                    LedgerAppRow(LedgerChainApp.Ethereum, LedgerAppDiscoveryState.InProgress),
                    LedgerAppRow(LedgerChainApp.Tron, LedgerAppDiscoveryState.Skipped(LedgerFailure.AppNotInstalled(LedgerAppId.Tron))),
                    LedgerAppRow(LedgerChainApp.Bitcoin, LedgerAppDiscoveryState.Pending),
                ),
            ),
            operation = LedgerOperationState.Idle,
            actions = object : LedgerPairingActions {},
            onClose = {},
            onSystemAction = {},
        )
    }
}

@Preview
@Composable
private fun LedgerPairingAccountsPreview() {
    ThemedPreview {
        LedgerPairingScreenInternal(
            state = LedgerPairingState(
                isRepair = false,
                step = LedgerPairingStep.Accounts,
                accounts = listOf(
                    LedgerAccountRow(0, PREVIEW_ADDRESSES, isAlreadyAdded = true),
                    LedgerAccountRow(1, PREVIEW_ADDRESSES, isAlreadyAdded = false),
                ),
                selected = setOf(1),
                canLoadMore = true,
            ),
            operation = LedgerOperationState.Idle,
            actions = object : LedgerPairingActions {},
            onClose = {},
            onSystemAction = {},
        )
    }
}

private val PREVIEW_ADDRESSES = listOf(
    LedgerAppAddress(LedgerChainApp.Ton, "UQBx4D1dAyHOVRsqVHpcu6ZbBiWxFM4pm6l3yH1OtCE5XjE_"),
    LedgerAppAddress(LedgerChainApp.Ethereum, "0xdaB2c1D0e9F8a7Cba2faCF3b5E1d2A0B7C9d397a"),
    LedgerAppAddress(LedgerChainApp.Tron, "TPcjJ3mJ1rWbZ9n3XoNqWz8Kc5mVdT1jzo6"),
    LedgerAppAddress(LedgerChainApp.Bitcoin, "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhr39d"),
    LedgerAppAddress(LedgerChainApp.Solana, "HDYQ7mZ5eXw1nQk3pV9tBq2fL8sR4uY6cH1oA3NzR"),
)

package com.tonapps.wallet.features.ledger.pairing

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDiscoveryErrorFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerTransport
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingAction
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingLastStep
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.bus.walletimport.WalletImportPairingSession
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.usb.LedgerUsbDevice
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.analytics.ledgerAppsSkipped
import com.tonapps.wallet.data.ledger.analytics.statusWord
import com.tonapps.wallet.data.ledger.analytics.toAnalyticsApps
import com.tonapps.wallet.data.ledger.analytics.toFailureType
import com.tonapps.wallet.data.ledger.analytics.toLedgerFlowModel
import com.tonapps.wallet.data.ledger.analytics.toWalletFlowModel
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice

internal class LedgerPairingAnalytics(
    private val mode: LedgerPairingMode,
    private val pairingSession: WalletImportPairingSession?,
) {

    private val events
        get() = AnalyticsHelper.Default.events

    private var isHandedOff = false

    fun started() {
        val migrate = mode as? LedgerPairingMode.Migrate ?: return
        events.ledgerFlow.ledgerMigrationStart(from = migrate.from)
    }

    fun readiness(readiness: LedgerBleReadiness) {
        pairingSession?.setBlocked(WalletImportPairingSession.Blocker.BluetoothOff, readiness == LedgerBleReadiness.BluetoothOff)
        if (readiness == LedgerBleReadiness.Ready) {
            pairingSession?.setBlocked(WalletImportPairingSession.Blocker.BluetoothPermissionDenied, isBlocked = false)
        }
    }

    fun permissionsResult(granted: Boolean) {
        pairingSession?.setBlocked(WalletImportPairingSession.Blocker.BluetoothPermissionDenied, !granted)
    }

    fun searchStarted() {
        pairingSession?.action(WalletFlowPairingAction.DeviceSearchStarted)
    }

    fun bleDevicesFound(devices: List<LedgerBleDevice>) {
        val session = pairingSession ?: return
        devices.forEach { device ->
            session.deviceFound(device.address, WalletFlowLedgerTransport.Bluetooth, device.model.toWalletFlowModel())
        }
    }

    fun usbDevicesFound(devices: List<LedgerUsbDevice>) {
        val session = pairingSession ?: return
        if (devices.isNotEmpty()) {
            session.action(WalletFlowPairingAction.DeviceSearchStarted)
        }
        devices.forEach { device ->
            session.deviceFound(device.deviceId, WalletFlowLedgerTransport.Usb, device.model?.toWalletFlowModel())
        }
    }

    fun handedOff() {
        isHandedOff = true
    }

    fun cleared() {
        if (!isHandedOff) {
            pairingSession?.exit()
        }
    }

    fun connected(device: LedgerDevice, kind: LedgerLinkKind) {
        pairingSession?.connected()
        val new = mode as? LedgerPairingMode.New ?: return
        val ledgerModel = device.model?.toWalletFlowModel()
        when (kind) {
            LedgerLinkKind.Bluetooth -> events.walletFlow.walletImportLedgerBluetoothConnected(
                walletMode = WalletFlowWalletMode.Multi,
                walletSource = WalletFlowWalletSource.Ledger,
                from = new.from,
                ledgerModel = ledgerModel,
            )
            LedgerLinkKind.Usb -> events.walletFlow.walletImportLedgerUsbConnected(
                walletMode = WalletFlowWalletMode.Multi,
                walletSource = WalletFlowWalletSource.Ledger,
                from = new.from,
                ledgerModel = ledgerModel,
            )
        }
    }

    fun tonAppOpened(device: LedgerDevice) {
        pairingSession?.advance(WalletFlowPairingLastStep.AppOpened)
        val new = mode as? LedgerPairingMode.New ?: return
        events.walletFlow.walletImportLedgerAppOpened(
            walletMode = WalletFlowWalletMode.Multi,
            walletSource = WalletFlowWalletSource.Ledger,
            from = new.from,
            ledgerModel = device.model?.toWalletFlowModel(),
        )
    }

    fun accountsListed(device: LedgerDevice, apps: Collection<LedgerChainApp>) {
        pairingSession?.setBlocked(WalletImportPairingSession.Blocker.ConnectionError, isBlocked = false)
        pairingSession?.advance(WalletFlowPairingLastStep.AccountsView)
        val new = mode as? LedgerPairingMode.New ?: return
        events.walletFlow.walletImportLedgerAccountsView(
            walletMode = WalletFlowWalletMode.Multi,
            walletSource = WalletFlowWalletSource.Ledger,
            from = new.from,
            ledgerModel = device.model?.toWalletFlowModel(),
            ledgerApps = apps.toAnalyticsApps(),
        )
    }

    fun appsSkipped(device: LedgerDevice, skipped: Map<LedgerChainApp, LedgerFailure>) {
        val from = when (mode) {
            is LedgerPairingMode.New -> LedgerDiscoveryErrorFrom.Import
            is LedgerPairingMode.Migrate -> LedgerDiscoveryErrorFrom.Migration
            is LedgerPairingMode.Repair -> LedgerDiscoveryErrorFrom.Repair
        }
        events.ledgerFlow.ledgerAppsSkipped(from, skipped, device.model)
    }

    fun failed(failure: LedgerFailure, device: LedgerDevice?) {
        val failureType = failure.toFailureType() ?: return
        pairingSession?.setBlocked(WalletImportPairingSession.Blocker.ConnectionError)
        when (mode) {
            is LedgerPairingMode.New -> events.walletFlow.walletImportError(
                walletMode = WalletFlowWalletMode.Multi,
                walletSource = WalletFlowWalletSource.Ledger,
                from = mode.from,
                errorType = failureType.key,
                errorCode = failure.statusWord,
                errorMessage = null,
                ledgerModel = device?.model?.toWalletFlowModel(),
            )
            is LedgerPairingMode.Migrate -> events.ledgerFlow.ledgerMigrationError(
                from = mode.from,
                failureType = failureType,
                ledgerModel = device?.model?.toLedgerFlowModel(),
            )
            is LedgerPairingMode.Repair -> Unit
        }
    }
}

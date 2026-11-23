package com.tonapps.wallet.features.ledger.pairing

import androidx.compose.runtime.Immutable
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.usb.LedgerUsbDevice
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerLegacyWallet
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice

internal sealed interface LedgerPairingMode {

    data class New(val from: WalletFlowFrom) : LedgerPairingMode

    data class Repair(val walletId: String) : LedgerPairingMode

    data class Migrate(val walletId: String, val from: LedgerMigrationStartFrom) : LedgerPairingMode
}

internal enum class LedgerPairingStep {
    Connect,
    Discovery,
    Accounts,
}

@Immutable
internal data class LedgerAccountRow(
    val index: Int,
    val addresses: List<LedgerAppAddress>,
    val isAlreadyAdded: Boolean,
)

@Immutable
internal data class LedgerAppAddress(
    val app: LedgerChainApp,
    val address: String,
)

@Immutable
internal data class LedgerAppRow(
    val app: LedgerChainApp,
    val state: LedgerAppDiscoveryState,
)

@Immutable
internal data class LedgerPairingState(
    val isRepair: Boolean,
    val isMigration: Boolean = false,
    val migration: LedgerLegacyWallet? = null,
    val step: LedgerPairingStep = LedgerPairingStep.Connect,
    val readiness: LedgerBleReadiness = LedgerBleReadiness.Ready,
    val isPermissionDenied: Boolean = false,
    val devices: List<LedgerBleDevice> = emptyList(),
    val usbDevices: List<LedgerUsbDevice> = emptyList(),
    val scanFailure: LedgerFailure? = null,
    val device: LedgerDevice? = null,
    val apps: List<LedgerAppRow> = emptyList(),
    val accounts: List<LedgerAccountRow> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val canLoadMore: Boolean = false,
    val isImporting: Boolean = false,
)

internal sealed interface LedgerPairingEvent {
    data class Import(val payload: String, val deviceName: String) : LedgerPairingEvent
    data object Repaired : LedgerPairingEvent
    data class Failed(val failure: LedgerFailure) : LedgerPairingEvent
    data object Close : LedgerPairingEvent
}

internal interface LedgerPairingActions {

    fun back() = Unit

    fun onPermissionsResult(granted: Boolean) = Unit

    fun retryScan() = Unit

    fun selectDevice(bleDevice: LedgerBleDevice) = Unit

    fun selectUsbDevice(usbDevice: LedgerUsbDevice) = Unit

    fun retryOperation() = Unit

    fun toggle(index: Int) = Unit

    fun loadMore() = Unit

    fun importSelected() = Unit
}

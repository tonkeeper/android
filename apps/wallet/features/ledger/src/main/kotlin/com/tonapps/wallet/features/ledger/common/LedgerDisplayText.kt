package com.tonapps.wallet.features.ledger.common

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionStatus

internal val LedgerProgress.displayText: String
    get() = when (this) {
        LedgerProgress.Connecting,
        LedgerProgress.Reconnecting -> CONNECTING
        LedgerProgress.Pairing -> PAIRING
        LedgerProgress.WaitingUsb -> PLUG_IN
        LedgerProgress.UsbAccess -> ALLOW_USB
        LedgerProgress.FinishPendingRequest -> BUSY
        LedgerProgress.WaitingUnlock -> LOCKED
        is LedgerProgress.OpeningApp -> app.displayName
        is LedgerProgress.ClosingApp -> openAppName
        is LedgerProgress.Working -> app?.displayName ?: LEDGER
        is LedgerProgress.Confirm -> REVIEW
        is LedgerProgress.VerifyAddress -> VERIFY
    }

internal val LedgerOperationState.displayText: String
    get() = when (this) {
        is LedgerOperationState.Active -> progress.displayText
        is LedgerOperationState.Failed -> failure.displayText
        is LedgerOperationState.Done -> DONE
        LedgerOperationState.Idle -> CONNECTING
    }

internal val LedgerConnectionStatus?.displayText: String
    get() = when (this) {
        null -> LEDGER
        is LedgerConnectionStatus.Busy -> progress.displayText
        is LedgerConnectionStatus.Connected -> app?.displayName ?: CONNECTED
        LedgerConnectionStatus.Nearby -> NEARBY
        LedgerConnectionStatus.UsbConnected -> USB
        LedgerConnectionStatus.UsbDisconnected -> UNPLUGGED
        LedgerConnectionStatus.Searching -> SEARCHING
        LedgerConnectionStatus.NotFound -> NOT_FOUND
        LedgerConnectionStatus.BluetoothOff -> BLUETOOTH_OFF
        LedgerConnectionStatus.NoPermission -> NO_ACCESS
        LedgerConnectionStatus.LocationOff -> LOCATION_OFF
        LedgerConnectionStatus.Unsupported -> UNSUPPORTED
    }

internal fun ledgerIntroDisplayText(readiness: LedgerBleReadiness, hasUsbDevice: Boolean): String = if (hasUsbDevice) {
    USB
} else {
    when (readiness) {
        LedgerBleReadiness.Ready -> SEARCHING
        LedgerBleReadiness.Unsupported -> UNSUPPORTED
        LedgerBleReadiness.BluetoothOff -> BLUETOOTH_OFF
        LedgerBleReadiness.LocationOff -> LOCATION_OFF
        is LedgerBleReadiness.MissingPermissions -> NO_ACCESS
    }
}

private val LedgerFailure.displayText: String
    get() = when (this) {
        LedgerFailure.Cancelled -> CANCELLED
        is LedgerFailure.UserRefused,
        LedgerFailure.PairingRefused -> REJECTED
        LedgerFailure.DeviceLocked -> LOCKED
        LedgerFailure.RequestExpired -> EXPIRED
        LedgerFailure.Busy -> BUSY
        LedgerFailure.BluetoothOff -> BLUETOOTH_OFF
        LedgerFailure.NoPermission,
        LedgerFailure.UsbAccessDenied -> NO_ACCESS
        LedgerFailure.DeviceNotFound -> NOT_FOUND
        LedgerFailure.UsbNotConnected -> UNPLUGGED
        is LedgerFailure.AppNotInstalled -> NO_APP
        is LedgerFailure.WrongAppOpen -> openAppName
        LedgerFailure.BluetoothUnavailable,
        LedgerFailure.ScanUnavailable,
        LedgerFailure.PairingLost,
        LedgerFailure.UnsupportedDevice,
        LedgerFailure.ConnectionLost,
        is LedgerFailure.Timeout,
        is LedgerFailure.SettingRequired,
        is LedgerFailure.AppUpdateRequired,
        is LedgerFailure.InvalidPayload,
        is LedgerFailure.AddressMismatch,
        is LedgerFailure.Unsupported,
        is LedgerFailure.DeviceError,
        is LedgerFailure.Unexpected -> ERROR
    }

private const val LEDGER = "Ledger"
private const val CONNECTING = "Connecting"
private const val CONNECTED = "Connected"
private const val PAIRING = "Pairing"
private const val PLUG_IN = "Plug in"
private const val ALLOW_USB = "Allow USB"
private const val BUSY = "Busy"
private const val LOCKED = "Locked"
private const val REVIEW = "Review"
private const val VERIFY = "Verify"
private const val DONE = "Done"
private const val CANCELLED = "Cancelled"
private const val REJECTED = "Rejected"
private const val EXPIRED = "Expired"
private const val NO_APP = "No app"
private const val ERROR = "Error"
private const val NEARBY = "Nearby"
private const val USB = "USB"
private const val UNPLUGGED = "Unplugged"
private const val SEARCHING = "Searching"
private const val NOT_FOUND = "Not found"
private const val BLUETOOTH_OFF = "BT off"
private const val NO_ACCESS = "No access"
private const val LOCATION_OFF = "GPS off"
private const val UNSUPPORTED = "No BLE"

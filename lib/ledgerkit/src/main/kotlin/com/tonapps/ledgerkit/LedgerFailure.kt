package com.tonapps.ledgerkit

import kotlinx.serialization.Serializable

@Serializable
sealed interface LedgerFailure {

    @Serializable
    data object Cancelled : LedgerFailure

    @Serializable
    data object BluetoothUnavailable : LedgerFailure

    @Serializable
    data object BluetoothOff : LedgerFailure

    @Serializable
    data object NoPermission : LedgerFailure

    @Serializable
    data object DeviceNotFound : LedgerFailure

    @Serializable
    data object UsbNotConnected : LedgerFailure

    @Serializable
    data object UsbAccessDenied : LedgerFailure

    @Serializable
    data object ScanUnavailable : LedgerFailure

    @Serializable
    data object PairingRefused : LedgerFailure

    @Serializable
    data object PairingLost : LedgerFailure

    @Serializable
    data object UnsupportedDevice : LedgerFailure

    @Serializable
    data object ConnectionLost : LedgerFailure

    @Serializable
    data object Busy : LedgerFailure

    @Serializable
    data object DeviceLocked : LedgerFailure

    @Serializable
    data object RequestExpired : LedgerFailure

    @Serializable
    data class Timeout(val phase: LedgerPhase) : LedgerFailure

    @Serializable
    data class AppNotInstalled(val app: LedgerAppId) : LedgerFailure

    @Serializable
    data class WrongAppOpen(val openAppName: String, val expected: LedgerAppId) : LedgerFailure

    @Serializable
    data class UserRefused(val statusWord: Int) : LedgerFailure

    @Serializable
    data class SettingRequired(val app: LedgerAppId, val setting: LedgerAppSetting) : LedgerFailure

    @Serializable
    data class AppUpdateRequired(
        val app: LedgerAppId,
        val installedVersion: String,
        val requiredVersion: String,
    ) : LedgerFailure

    @Serializable
    data class InvalidPayload(val statusWord: Int, val reason: String) : LedgerFailure

    @Serializable
    data class AddressMismatch(val app: LedgerAppId) : LedgerFailure

    @Serializable
    data class Unsupported(val reason: String) : LedgerFailure

    @Serializable
    data class DeviceError(val statusWord: Int) : LedgerFailure

    @Serializable
    data class Unexpected(val reason: String) : LedgerFailure
}

enum class LedgerAppSetting {
    BlindSigning,
    RawMessages,
    DataAllowed,
    CustomContracts,
    SignByHash,
}

enum class LedgerPhase {
    Scan,
    Connect,
    Setup,
    Reconnect,
    Apdu,
    Unlock,
}

class LedgerFailureException(
    val failure: LedgerFailure,
    val isPresented: Boolean = false,
    cause: Throwable? = null,
) : Exception(failure.toString(), cause)

fun LedgerFailure.asException(): LedgerFailureException = LedgerFailureException(this)

val LedgerFailure.isRetryable: Boolean
    get() = when (this) {
        LedgerFailure.Cancelled,
        LedgerFailure.RequestExpired,
        is LedgerFailure.Unsupported,
        is LedgerFailure.InvalidPayload,
        is LedgerFailure.AddressMismatch -> false
        else -> true
    }

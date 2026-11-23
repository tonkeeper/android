package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDiscoveryErrorFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowFailureType
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerApp
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerModel
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerSetting
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerTransport
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowPurpose
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.signing.LedgerDismissedException
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException

fun LedgerFailure.toFailureType(): LedgerFlowFailureType? = when (this) {
    LedgerFailure.Cancelled -> null
    LedgerFailure.BluetoothUnavailable -> LedgerFlowFailureType.BluetoothUnavailable
    LedgerFailure.BluetoothOff -> LedgerFlowFailureType.BluetoothOff
    LedgerFailure.NoPermission -> LedgerFlowFailureType.NoPermission
    LedgerFailure.DeviceNotFound -> LedgerFlowFailureType.DeviceNotFound
    LedgerFailure.UsbNotConnected -> LedgerFlowFailureType.UsbNotConnected
    LedgerFailure.UsbAccessDenied -> LedgerFlowFailureType.UsbAccessDenied
    LedgerFailure.ScanUnavailable -> LedgerFlowFailureType.ScanUnavailable
    LedgerFailure.PairingRefused -> LedgerFlowFailureType.PairingRefused
    LedgerFailure.PairingLost -> LedgerFlowFailureType.PairingLost
    LedgerFailure.UnsupportedDevice -> LedgerFlowFailureType.UnsupportedDevice
    LedgerFailure.ConnectionLost -> LedgerFlowFailureType.ConnectionLost
    LedgerFailure.Busy -> LedgerFlowFailureType.Busy
    LedgerFailure.DeviceLocked -> LedgerFlowFailureType.DeviceLocked
    LedgerFailure.RequestExpired -> LedgerFlowFailureType.RequestExpired
    is LedgerFailure.Timeout -> LedgerFlowFailureType.Timeout
    is LedgerFailure.AppNotInstalled -> LedgerFlowFailureType.AppNotInstalled
    is LedgerFailure.WrongAppOpen -> LedgerFlowFailureType.WrongAppOpen
    is LedgerFailure.UserRefused -> LedgerFlowFailureType.UserRefused
    is LedgerFailure.SettingRequired -> LedgerFlowFailureType.SettingRequired
    is LedgerFailure.AppUpdateRequired -> LedgerFlowFailureType.AppUpdateRequired
    is LedgerFailure.InvalidPayload -> LedgerFlowFailureType.InvalidPayload
    is LedgerFailure.AddressMismatch -> LedgerFlowFailureType.AddressMismatch
    is LedgerFailure.Unsupported -> LedgerFlowFailureType.Unsupported
    is LedgerFailure.DeviceError -> LedgerFlowFailureType.DeviceError
    is LedgerFailure.Unexpected -> LedgerFlowFailureType.Unexpected
}

val Throwable.ledgerFailure: LedgerFailure?
    get() = when (this) {
        is LedgerFailureException -> failure
        is LedgerDismissedException -> failure
        else -> null
    }

fun Throwable.toLedgerFailureType(): LedgerFlowFailureType? {
    val failure = ledgerFailure ?: return LedgerFlowFailureType.Unexpected
    return failure.toFailureType()
}

val LedgerFailure.appId: LedgerAppId?
    get() = when (this) {
        is LedgerFailure.AppNotInstalled -> app
        is LedgerFailure.WrongAppOpen -> expected
        is LedgerFailure.SettingRequired -> app
        is LedgerFailure.AppUpdateRequired -> app
        is LedgerFailure.AddressMismatch -> app
        LedgerFailure.Cancelled,
        LedgerFailure.BluetoothUnavailable,
        LedgerFailure.BluetoothOff,
        LedgerFailure.NoPermission,
        LedgerFailure.DeviceNotFound,
        LedgerFailure.UsbNotConnected,
        LedgerFailure.UsbAccessDenied,
        LedgerFailure.ScanUnavailable,
        LedgerFailure.PairingRefused,
        LedgerFailure.PairingLost,
        LedgerFailure.UnsupportedDevice,
        LedgerFailure.ConnectionLost,
        LedgerFailure.Busy,
        LedgerFailure.DeviceLocked,
        LedgerFailure.RequestExpired,
        is LedgerFailure.Timeout,
        is LedgerFailure.UserRefused,
        is LedgerFailure.InvalidPayload,
        is LedgerFailure.Unsupported,
        is LedgerFailure.DeviceError,
        is LedgerFailure.Unexpected -> null
    }

val LedgerFailure.statusWord: Int?
    get() = when (this) {
        is LedgerFailure.UserRefused -> statusWord
        is LedgerFailure.InvalidPayload -> statusWord
        is LedgerFailure.DeviceError -> statusWord
        LedgerFailure.Cancelled,
        LedgerFailure.BluetoothUnavailable,
        LedgerFailure.BluetoothOff,
        LedgerFailure.NoPermission,
        LedgerFailure.DeviceNotFound,
        LedgerFailure.UsbNotConnected,
        LedgerFailure.UsbAccessDenied,
        LedgerFailure.ScanUnavailable,
        LedgerFailure.PairingRefused,
        LedgerFailure.PairingLost,
        LedgerFailure.UnsupportedDevice,
        LedgerFailure.ConnectionLost,
        LedgerFailure.Busy,
        LedgerFailure.DeviceLocked,
        LedgerFailure.RequestExpired,
        is LedgerFailure.Timeout,
        is LedgerFailure.AppNotInstalled,
        is LedgerFailure.WrongAppOpen,
        is LedgerFailure.SettingRequired,
        is LedgerFailure.AppUpdateRequired,
        is LedgerFailure.AddressMismatch,
        is LedgerFailure.Unsupported,
        is LedgerFailure.Unexpected -> null
    }

fun LedgerAppId.toLedgerFlowApp(): LedgerFlowLedgerApp = when (this) {
    LedgerAppId.Ton -> LedgerFlowLedgerApp.Ton
    LedgerAppId.Ethereum -> LedgerFlowLedgerApp.Ethereum
    LedgerAppId.Tron -> LedgerFlowLedgerApp.Tron
    LedgerAppId.Bitcoin -> LedgerFlowLedgerApp.Bitcoin
    LedgerAppId.Solana -> LedgerFlowLedgerApp.Solana
}

fun Iterable<LedgerChainApp>.toAnalyticsApps(): String = map { it.appId.toLedgerFlowApp().key }
    .sorted()
    .joinToString(separator = ",")

fun Events.LedgerFlow.ledgerAppsSkipped(from: LedgerDiscoveryErrorFrom, skipped: Map<LedgerChainApp, LedgerFailure>, model: LedgerModel?) {
    for ((app, reason) in skipped) {
        val failureType = reason.toFailureType() ?: continue
        ledgerDiscoveryError(
            from = from,
            ledgerApp = app.appId.toLedgerFlowApp(),
            failureType = failureType,
            ledgerModel = model?.toLedgerFlowModel(),
        )
    }
}

fun LedgerAppSetting.toLedgerFlowSetting(): LedgerFlowLedgerSetting = when (this) {
    LedgerAppSetting.BlindSigning -> LedgerFlowLedgerSetting.BlindSigning
    LedgerAppSetting.RawMessages -> LedgerFlowLedgerSetting.RawMessages
    LedgerAppSetting.DataAllowed -> LedgerFlowLedgerSetting.DataAllowed
    LedgerAppSetting.CustomContracts -> LedgerFlowLedgerSetting.CustomContracts
    LedgerAppSetting.SignByHash -> LedgerFlowLedgerSetting.SignByHash
}

fun LedgerPurpose.toLedgerFlowPurpose(): LedgerFlowPurpose = when (this) {
    LedgerPurpose.Connect -> LedgerFlowPurpose.Connect
    LedgerPurpose.ImportAccounts -> LedgerFlowPurpose.ImportAccounts
    LedgerPurpose.AddAccounts -> LedgerFlowPurpose.AddAccounts
    LedgerPurpose.VerifyAddress -> LedgerFlowPurpose.VerifyAddress
    LedgerPurpose.SignTransaction -> LedgerFlowPurpose.SignTransaction
    LedgerPurpose.SignMessage -> LedgerFlowPurpose.SignMessage
    LedgerPurpose.SignProof -> LedgerFlowPurpose.SignProof
    LedgerPurpose.CreateWallet -> LedgerFlowPurpose.CreateWallet
}

fun LedgerLinkKind.toLedgerFlowTransport(): LedgerFlowLedgerTransport = when (this) {
    LedgerLinkKind.Bluetooth -> LedgerFlowLedgerTransport.Bluetooth
    LedgerLinkKind.Usb -> LedgerFlowLedgerTransport.Usb
}

fun LedgerModel.toLedgerFlowModel(): LedgerFlowLedgerModel = when (this) {
    LedgerModel.NanoS -> LedgerFlowLedgerModel.NanoS
    LedgerModel.NanoSPlus -> LedgerFlowLedgerModel.NanoSPlus
    LedgerModel.NanoX -> LedgerFlowLedgerModel.NanoX
    LedgerModel.Stax -> LedgerFlowLedgerModel.Stax
    LedgerModel.Flex -> LedgerFlowLedgerModel.Flex
    LedgerModel.NanoGen5 -> LedgerFlowLedgerModel.NanoGen5
}

fun LedgerModel.toWalletFlowModel(): WalletFlowLedgerModel = when (this) {
    LedgerModel.NanoS -> WalletFlowLedgerModel.NanoS
    LedgerModel.NanoSPlus -> WalletFlowLedgerModel.NanoSPlus
    LedgerModel.NanoX -> WalletFlowLedgerModel.NanoX
    LedgerModel.Stax -> WalletFlowLedgerModel.Stax
    LedgerModel.Flex -> WalletFlowLedgerModel.Flex
    LedgerModel.NanoGen5 -> WalletFlowLedgerModel.NanoGen5
}

fun LedgerModel.toTransactionSentModel(): TransactionSentLedgerModel = when (this) {
    LedgerModel.NanoS -> TransactionSentLedgerModel.NanoS
    LedgerModel.NanoSPlus -> TransactionSentLedgerModel.NanoSPlus
    LedgerModel.NanoX -> TransactionSentLedgerModel.NanoX
    LedgerModel.Stax -> TransactionSentLedgerModel.Stax
    LedgerModel.Flex -> TransactionSentLedgerModel.Flex
    LedgerModel.NanoGen5 -> TransactionSentLedgerModel.NanoGen5
}

suspend fun McAccountRepository.ledgerModel(walletId: String): LedgerModel? = try {
    getHardwareDescriptor(walletId)?.deviceModel?.let(LedgerModel::fromName)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LedgerNonFatals.report(e, LedgerIssueSite.Storage)
    null
}

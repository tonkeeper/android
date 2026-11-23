package com.tonapps.ledgerkit.apdu

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException

object StatusWord {
    const val OK = 0x9000
    const val DEVICE_LOCKED = 0x5515
    const val ACTION_REFUSED = 0x5501
    const val PIN_NOT_SET = 0x5502
    const val DEVICE_INTERNAL_ERROR = 0x5223
    const val CLA_NOT_SUPPORTED = 0x6e00
    const val INS_NOT_SUPPORTED = 0x6d00
    const val APP_NOT_FOUND = 0x6807
    const val APP_NAME_MISSING = 0x670a
    const val SECURITY_STATUS_NOT_SATISFIED = 0x6982
    const val CONDITIONS_NOT_SATISFIED = 0x6985
    const val INVALID_DATA = 0x6a80
    const val SDK_NOT_SUPPORTED = 0x6808
    const val WRONG_P1_P2 = 0x6b00
    const val WRONG_LENGTH = 0x6700
    const val TX_TYPE_NOT_SUPPORTED = 0x6501
    const val CHAIN_ID_BUFFER_TOO_SMALL = 0x6502
    const val DASHBOARD_APP_NOT_OPEN = 0x6511
    const val DASHBOARD_APP_NOT_OPEN_ALT = 0x650e
    const val LOCKED_ALT = 0x6b0c
    const val TON_BLIND_SIGNING_DISABLED = 0xbd00
    const val TRON_DATA_ALLOWED_REQUIRED = 0x6a8b
    const val TRON_SIGN_BY_HASH_REQUIRED = 0x6a8c
    const val TRON_CUSTOM_CONTRACTS_REQUIRED = 0x6a8d
    const val COMMAND_NOT_ALLOWED = 0x6980
    const val REFERENCED_DATA_NOT_FOUND = 0x6a88
    const val INSUFFICIENT_MEMORY = 0x6a84
    const val DEVICE_BUSY = 0x6901
    const val LOCKED_PIN = 0x5303
    const val SOLANA_SIGNER_NOT_FOUND = 0x6a81
    const val SOLANA_MESSAGE_TOO_LARGE = 0x6a83

    fun isTechnicalProblem(statusWord: Int): Boolean = statusWord in 0x6f00..0x6fff
}

enum class LedgerOperation {
    AppInfo,
    OpenApp,
    QuitApp,
    Address,
    Configuration,
    SignTransaction,
    SignMessage,
    SignTypedData,
    SignProof,
    Other,
}

object StatusWordMapper {

    fun map(statusWord: Int, app: LedgerAppId?, operation: LedgerOperation): LedgerFailure = when (statusWord) {
        StatusWord.DEVICE_LOCKED,
        StatusWord.LOCKED_ALT,
        StatusWord.LOCKED_PIN,
        StatusWord.SECURITY_STATUS_NOT_SATISFIED -> LedgerFailure.DeviceLocked
        StatusWord.CONDITIONS_NOT_SATISFIED,
        StatusWord.ACTION_REFUSED -> LedgerFailure.UserRefused(statusWord)
        StatusWord.PIN_NOT_SET -> LedgerFailure.DeviceError(statusWord)
        StatusWord.APP_NOT_FOUND -> app?.let { LedgerFailure.AppNotInstalled(it) } ?: LedgerFailure.DeviceError(statusWord)
        StatusWord.INVALID_DATA -> LedgerFailure.InvalidPayload(statusWord, "Invalid data")
        StatusWord.SDK_NOT_SUPPORTED -> if (app == LedgerAppId.Solana && operation == LedgerOperation.SignTransaction) {
            LedgerFailure.SettingRequired(LedgerAppId.Solana, LedgerAppSetting.BlindSigning)
        } else {
            LedgerFailure.InvalidPayload(statusWord, "Unsupported request")
        }
        StatusWord.SOLANA_SIGNER_NOT_FOUND -> solanaPayload(statusWord, app, "The transaction is not signed by this account")
        StatusWord.SOLANA_MESSAGE_TOO_LARGE -> solanaPayload(statusWord, app, "Request is too large for the device")
        StatusWord.TON_BLIND_SIGNING_DISABLED -> LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning)
        StatusWord.TRON_DATA_ALLOWED_REQUIRED -> tronSetting(statusWord, app, LedgerAppSetting.DataAllowed)
        StatusWord.TRON_SIGN_BY_HASH_REQUIRED -> tronSetting(statusWord, app, LedgerAppSetting.SignByHash)
        StatusWord.TRON_CUSTOM_CONTRACTS_REQUIRED -> tronSetting(statusWord, app, LedgerAppSetting.CustomContracts)
        StatusWord.DEVICE_BUSY -> LedgerFailure.Busy
        StatusWord.INSUFFICIENT_MEMORY -> if (app == LedgerAppId.Solana) {
            LedgerFailure.InvalidPayload(statusWord, "The derivation path is not supported by the device app")
        } else {
            LedgerFailure.InvalidPayload(statusWord, "Request is too large for the device")
        }
        StatusWord.COMMAND_NOT_ALLOWED -> LedgerFailure.DeviceError(statusWord)
        StatusWord.INS_NOT_SUPPORTED,
        StatusWord.CLA_NOT_SUPPORTED,
        StatusWord.DASHBOARD_APP_NOT_OPEN,
        StatusWord.DASHBOARD_APP_NOT_OPEN_ALT -> app?.let {
            LedgerFailure.WrongAppOpen(openAppName = "", expected = it)
        } ?: LedgerFailure.DeviceError(statusWord)
        StatusWord.TX_TYPE_NOT_SUPPORTED -> LedgerFailure.InvalidPayload(statusWord, "Transaction type is not supported by the device app")
        StatusWord.CHAIN_ID_BUFFER_TOO_SMALL -> LedgerFailure.InvalidPayload(statusWord, "Chain id is not supported by the device app")
        StatusWord.WRONG_LENGTH,
        StatusWord.WRONG_P1_P2,
        StatusWord.APP_NAME_MISSING -> LedgerFailure.InvalidPayload(statusWord, "Malformed request")
        else -> LedgerFailure.DeviceError(statusWord)
    }

    private fun solanaPayload(statusWord: Int, app: LedgerAppId?, reason: String): LedgerFailure =
        if (app == LedgerAppId.Solana) {
            LedgerFailure.InvalidPayload(statusWord, reason)
        } else {
            LedgerFailure.DeviceError(statusWord)
        }

    private fun tronSetting(statusWord: Int, app: LedgerAppId?, setting: LedgerAppSetting): LedgerFailure =
        if (app == LedgerAppId.Tron) {
            LedgerFailure.SettingRequired(LedgerAppId.Tron, setting)
        } else {
            LedgerFailure.DeviceError(statusWord)
        }
}

fun ApduResponse.requireOk(app: LedgerAppId?, operation: LedgerOperation): ByteArray {
    if (!isOk) {
        throw LedgerFailureException(StatusWordMapper.map(statusWord, app, operation))
    }
    return data
}

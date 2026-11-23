package com.tonapps.wallet.data.ledger.ton

import com.tonapps.ledger.transport.TransportStatusException
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.StatusWord
import kotlinx.coroutines.CancellationException

internal suspend fun <T> mapTonTransportErrors(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: LedgerFailureException) {
    throw e
} catch (e: TransportStatusException) {
    throw LedgerFailureException(e.toLedgerFailure())
} catch (e: Exception) {
    throw LedgerFailureException(LedgerFailure.InvalidPayload(0, e.message ?: "TON app request failed"), cause = e)
}

private fun TransportStatusException.toLedgerFailure(): LedgerFailure = when (this) {
    is TransportStatusException.DeniedByUser -> LedgerFailure.UserRefused(0x6985)
    is TransportStatusException.SecurityNotSatisfied -> LedgerFailure.DeviceLocked
    is TransportStatusException.LockedDevice -> LedgerFailure.DeviceLocked
    is TransportStatusException.BlindSigningDisabled -> LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning)
    is TransportStatusException.AppNotInstalled -> LedgerFailure.AppNotInstalled(LedgerAppId.Ton)
    is TransportStatusException.InvalidDataReceived -> LedgerFailure.InvalidPayload(0x6a80, "The TON app rejected the transaction")
    is TransportStatusException.InvalidParameterReceived -> LedgerFailure.InvalidPayload(0x6b00, "Malformed request")
    is TransportStatusException.IncorrectLength -> LedgerFailure.InvalidPayload(0x6700, "Malformed request")
    is TransportStatusException.MissingCriticalParameter -> LedgerFailure.InvalidPayload(0x6800, "Malformed request")
    is TransportStatusException.TxParsingFailed -> LedgerFailure.InvalidPayload(TON_PARSE_FAILED, "The TON app could not read this transaction")
    is TransportStatusException.InternalError -> LedgerFailure.DeviceError(0x6f00)
    is TransportStatusException.UnknownError -> when (code) {
        TON_REQUEST_TOO_LONG -> LedgerFailure.InvalidPayload(code, "The request is too long for the TON app")
        StatusWord.LOCKED_PIN, StatusWord.LOCKED_ALT -> LedgerFailure.DeviceLocked
        StatusWord.DEVICE_BUSY -> LedgerFailure.Busy
        else -> LedgerFailure.DeviceError(code)
    }
}

internal const val TON_PARSE_FAILED = 0xb010
private const val TON_REQUEST_TOO_LONG = 0xb00b


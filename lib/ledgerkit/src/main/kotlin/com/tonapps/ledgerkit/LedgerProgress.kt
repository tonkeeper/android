package com.tonapps.ledgerkit

sealed interface LedgerProgress {

    data object Connecting : LedgerProgress

    data object Pairing : LedgerProgress

    data object WaitingUsb : LedgerProgress

    data object UsbAccess : LedgerProgress

    data object FinishPendingRequest : LedgerProgress

    data object WaitingUnlock : LedgerProgress

    data class OpeningApp(val app: LedgerAppId) : LedgerProgress

    data class ClosingApp(val openAppName: String, val target: LedgerAppId) : LedgerProgress

    data object Reconnecting : LedgerProgress

    data class Working(val app: LedgerAppId?) : LedgerProgress

    data class Confirm(
        val app: LedgerAppId,
        val kind: LedgerConfirmKind,
        val step: Int = 1,
        val totalSteps: Int = 1,
        val detail: LedgerConfirmDetail? = null,
    ) : LedgerProgress

    data class VerifyAddress(val app: LedgerAppId, val address: String) : LedgerProgress
}

sealed interface LedgerConfirmDetail {

    data class Hash(val hex: String) : LedgerConfirmDetail

    data class MessageHash(val base58: String) : LedgerConfirmDetail

    data class BitcoinChange(val amountSats: Long, val address: String) : LedgerConfirmDetail
}

enum class LedgerConfirmKind {
    Transaction,
    Message,
    TypedData,
    Proof,
    Wallet,
}

package com.tonapps.ledgerkit.controller

data class LedgerRequest(
    val deviceId: String,
    val deviceName: String?,
    val purpose: LedgerPurpose,
    val walletId: String? = null,
)

enum class LedgerPurpose {
    Connect,
    ImportAccounts,
    AddAccounts,
    VerifyAddress,
    SignTransaction,
    SignMessage,
    SignProof,
    CreateWallet,
}

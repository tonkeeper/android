package com.tonapps.wc.models

import kotlinx.serialization.SerialName

enum class WcMethod(
    val methodName: String
) {
    // TRON
    @SerialName("tron_signMessage")
    TronSignMessage("tron_signMessage"),

    @SerialName("tron_signTransaction")
    TronSignTransaction("tron_signTransaction"),

    // ETH
    @SerialName("personal_sign")
    EthPersonalSign("personal_sign"),

    @SerialName("eth_signTypedData_v4")
    EthSignTypedDataV4("eth_signTypedData_v4"),

    @SerialName("eth_signTransaction")
    EthSignTransaction("eth_signTransaction"),

    @SerialName("eth_sendTransaction")
    EthSendTransaction("eth_sendTransaction"),

    @SerialName("wallet_getCapabilities")
    WalletGetCapabilities("wallet_getCapabilities"),

    @SerialName("wallet_watchAsset")
    WalletWatchAsset("wallet_watchAsset"),

    // SOLANA
    @SerialName("solana_getAccounts")
    SolanaGetAccounts("solana_getAccounts"),

    @SerialName("solana_requestAccounts")
    SolanaRequestAccounts("solana_requestAccounts"),

    @SerialName("solana_signMessage")
    SolanaSignMessage("solana_signMessage"),

    @SerialName("solana_signTransaction")
    SolanaSignTransaction("solana_signTransaction"),

    @SerialName("solana_signAllTransactions")
    SolanaSignAllTransactions("solana_signAllTransactions"),

    @SerialName("solana_signAndSendTransaction")
    SolanaSignAndSendTransaction("solana_signAndSendTransaction"),
    ;

    companion object {
        fun findByName(method: String): WcMethod? {
            return values()
                .firstOrNull { it.methodName == method }
        }
    }
}

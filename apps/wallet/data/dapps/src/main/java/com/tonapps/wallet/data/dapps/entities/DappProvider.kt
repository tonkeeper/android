package com.tonapps.wallet.data.dapps.entities

enum class DappProvider(val value: String) {
    TonConnect("ton_connect"),
    WalletConnect("wallet_connect");

    companion object {
        fun fromValue(value: String): DappProvider {
            return entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown DappProvider: $value")
        }
    }
}

package com.tonapps.wallet.data.account

enum class WalletType(val id: Int) {
    Default(0), Watch(1), Testnet(2), Signer(3), Lockup(4), Ledger(5), SignerQR(6)
}

fun walletType(id: Int): WalletType {
    return WalletType.entries.find { it.id == id } ?: WalletType.Default
}

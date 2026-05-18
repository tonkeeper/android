package com.tonapps.blockchain.contract

sealed interface WalletType {
    object Mnemonic : WalletType
    object WatchOnly : WalletType
    object Hardware : WalletType
}

sealed interface AbstractionType {
    object External : AbstractionType
    object SmartContract : AbstractionType
}

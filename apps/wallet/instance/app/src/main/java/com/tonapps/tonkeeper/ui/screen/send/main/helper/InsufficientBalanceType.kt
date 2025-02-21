package com.tonapps.tonkeeper.ui.screen.send.main.helper

enum class InsufficientBalanceType {
    EmptyBalance,
    EmptyJettonBalance,
    InsufficientTONBalance,
    InsufficientJettonBalance,
    InsufficientGaslessBalance,
    InsufficientBalanceWithFee,
    InsufficientBalanceForFee
}

fun InsufficientBalanceType.isTON(): Boolean {
    return this == InsufficientBalanceType.EmptyBalance || this == InsufficientBalanceType.InsufficientTONBalance || this == InsufficientBalanceType.InsufficientBalanceWithFee || this == InsufficientBalanceType.InsufficientBalanceForFee
}

fun InsufficientBalanceType.isEmptyBalance(): Boolean {
    return this == InsufficientBalanceType.EmptyBalance || this == InsufficientBalanceType.EmptyJettonBalance
}
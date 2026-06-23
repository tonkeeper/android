package com.tonapps.blockchain.model.legacy.errors

enum class InsufficientBalanceType {
    EmptyBalance,
    EmptyJettonBalance,
    InsufficientTONBalance,
    InsufficientJettonBalance,
    InsufficientGaslessBalance,
    InsufficientBalanceWithFee,
    InsufficientBatteryChargesForFee,
    InsufficientBalanceForFee,
}

fun InsufficientBalanceType.isTON(): Boolean {
    return this == InsufficientBalanceType.EmptyBalance || this == InsufficientBalanceType.InsufficientTONBalance || this == InsufficientBalanceType.InsufficientBalanceWithFee || this == InsufficientBalanceType.InsufficientBalanceForFee
}

fun InsufficientBalanceType.isEmptyBalance(): Boolean {
    return this == InsufficientBalanceType.EmptyBalance || this == InsufficientBalanceType.EmptyJettonBalance
}
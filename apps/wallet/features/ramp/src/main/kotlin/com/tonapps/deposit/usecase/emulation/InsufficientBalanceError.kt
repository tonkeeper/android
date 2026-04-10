package com.tonapps.deposit.usecase.emulation

import com.tonapps.icu.Coins

data class InsufficientBalanceError(
    val accountBalance: Coins,
    val totalAmount: Coins
) : RuntimeException(
    "Insufficient balance: have $accountBalance, need $totalAmount"
)
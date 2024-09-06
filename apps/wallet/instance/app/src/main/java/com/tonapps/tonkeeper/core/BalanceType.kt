package com.tonapps.tonkeeper.core

import com.tonapps.icu.Coins

object BalanceType {
    const val Zero = 0
    const val Positive = 1
    const val Huge = 2

    fun getBalanceType(value: Coins): Int {
        return when {
            value >= Coins.of(20.0) -> Huge
            value >= Coins.of(0.1) -> Positive
            else -> Zero
        }
    }
}
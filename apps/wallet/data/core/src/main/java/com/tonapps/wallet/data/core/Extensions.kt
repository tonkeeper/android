package com.tonapps.wallet.data.core

import java.math.BigDecimal
import java.math.MathContext

fun accountId(accountId: String, testnet: Boolean): String {
    if (testnet) {
        return "testnet:$accountId"
    }
    return accountId
}

package com.tonapps.wallet.data.core

fun accountId(accountId: String, testnet: Boolean): String {
    if (testnet) {
        return "testnet:$accountId"
    }
    return accountId
}
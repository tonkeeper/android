package com.tonapps.tonkeeper.api.base

class AccountKey(
    private val accountId: String,
    val testnet: Boolean
) {

    override fun toString(): String {
        if (testnet) {
            return "testnet:$accountId"
        }
        return accountId
    }
}

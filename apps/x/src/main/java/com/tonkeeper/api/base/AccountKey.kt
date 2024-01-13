package com.tonkeeper.api.base

class AccountKey(
    private val accountId: String,
    private val testnet: Boolean
) {

    override fun toString(): String {
        if (testnet) {
            return "testnet:$accountId"
        }
        return accountId
    }
}

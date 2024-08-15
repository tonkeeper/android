package com.tonapps.wallet.data.settings

enum class SpamTransactionState(val state: Int) {
    UNKNOWN(0), SPAM(1), NOT_SPAM(2)
}
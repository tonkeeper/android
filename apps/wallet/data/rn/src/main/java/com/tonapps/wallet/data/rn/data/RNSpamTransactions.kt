package com.tonapps.wallet.data.rn.data

class RNSpamTransactions(
    val walletId: String,
    val spam: List<String> = emptyList(),
    val nonSpam: List<String> = emptyList()
)
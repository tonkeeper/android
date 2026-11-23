package com.tonapps.ledgerkit

enum class LedgerAppId(val openName: String) {
    Ton("TON"),
    Ethereum("Ethereum"),
    Tron("Tron"),
    Bitcoin("Bitcoin"),
    Solana("Solana");

    companion object {
        fun fromOpenName(name: String): LedgerAppId? = entries.firstOrNull { it.openName == name }
    }
}

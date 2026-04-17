package com.tonapps.blockchain.contract

enum class Blockchain(
    val id: String
) {
    TON("TON"),
    TRON("TRON");
}

val Blockchain.mainCoin: CoinType
    get() = when (this) {
        Blockchain.TON -> CoinType.Ton
        Blockchain.TRON -> CoinType.Tron
    }
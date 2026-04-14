package com.tonapps.blockchain.contract

sealed class CoinType(
    val symbol: String,
    val decimals: Int,
) {
    object Ton : CoinType("TON", 18)
    object Tron : CoinType("TRON", 18)
}

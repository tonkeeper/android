package com.tonapps.blockchain.model.account

sealed interface Asset {

    val id: String
    val chain: Chain

    data class Coin(
        override val id: String,
        override val chain: Chain,
    ) : Asset

    data class Token(
        override val id: String,
        override val chain: Chain,
        val contract: String,
        val type: TokenType,
        val symbol: String = "",
        val tokenId: String = "",
        val decimals: Int = chain.decimals,
    ) : Asset
}

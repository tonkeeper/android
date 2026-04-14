package com.tonapps.blockchain.contract

import com.tonapps.blockchain.utils.toValue
import java.math.BigDecimal
import java.math.BigInteger

sealed interface Asset {

    val contract: String?
    val chain: Blockchain

    data class Coin(
        override val chain: Blockchain,
        val coin: CoinType,
    ) : Asset {
        override val contract: Nothing?
            get() = null
    }

    data class Token(
        override val contract: String,
        override val chain: Blockchain,

        val type: TokenType,

        /**
         * Token symbol used for different protocols such as swap
         */
        val symbol: String = "",

        /**
         * Use for NFT tokens like ERC-721 and ERC-1155
         */
        val tokenId: String = "",

        /**
         * Solana tokens may have arbitrary amount of decimals, so we need to store it separately.
         * Extend to the Unit if more usage found.
         */
        val decimals: Int = chain.mainCoin.decimals,
    ) : Asset {
        init {
            require(contract.isNotEmpty()) { "contract shouldn't be empty" }
        }
    }
}

fun Asset.toValue(value: BigInteger): BigDecimal {
    val decimals = when (this) {
        is Asset.Coin -> coin.decimals
        is Asset.Token -> decimals
    }
    return value.toValue(decimals)
}

fun Asset.toUnit(value: BigDecimal): BigInteger {
    val decimals = when (this) {
        is Asset.Coin -> coin.decimals
        is Asset.Token -> decimals
    }
    return value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()
}

fun Asset.getSymbol(): String {
    return when (this) {
        is Asset.Coin -> coin.symbol
        is Asset.Token -> symbol
    }
}

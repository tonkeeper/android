package com.tonkeeper.core.currency

import com.tonkeeper.core.Blockchain

data class Token(
    override val name: String,
    override val code: String,
    override val symbol: String? = null,
    override val decimals: Int = 9,
    val contractAddress: String,
    val blockchain: Blockchain
): Currency {

    constructor(
        name: String,
        code: String,
        decimals: Int,
        contractAddress: String,
        blockchain: Blockchain
    ) : this(name, code, null, decimals, contractAddress, blockchain)

    object TON {
        val USDT = Token("Tether USD", "USDT", "₮", 6, "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe", Blockchain.Companion.TON)
        val USDe = Token("Ether USD", "USDe", 6, "0:086fa2a675f74347b08dd4606a549b8fdb98829cb282bc1949d3b12fbaed9dcc", Blockchain.Companion.TON)
    }

    object TRON {
        val USDT = Token("Tether USD", "USDT", "₮", 6, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", Blockchain.Companion.TRON)
    }

    object ETH {
        val USDT = Token("Tether USD", "USDT", "₮", 6, "0xdac17f958d2ee523a2206206994597c13d831ec7", Blockchain.Companion.ETH)
    }


}
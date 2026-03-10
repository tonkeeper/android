package com.tonkeeper.core.currency

import com.tonkeeper.core.Blockchain

data class Crypto(
    override val name: String,
    override val decimals: Int = 9,
    override val symbol: String? = null,
    val blockchain: Blockchain
): Currency {

    constructor(name: String, decimals: Int, blockchain: Blockchain) : this(name, decimals, null, blockchain)

    override val code: String
        get() = blockchain.nativeTicker

    companion object {
        val TON = Crypto("Toncoin", 9, Blockchain.Companion.TON)
        val BTC = Crypto("Bitcoin", 8, "₿", Blockchain.Companion.BTC)
        val ETH = Crypto("Ethereum", 18, "Ξ", Blockchain.Companion.ETH)
        val LTC = Crypto("Litecoin", 8, "Ł", Blockchain.Companion.LTC)
        val DOGE = Crypto("Dogecoin", 8, "Ð", Blockchain.Companion.DOGE)
        val XMR = Crypto("Monero", 12, "ɱ", Blockchain.Companion.XMR)
        val XRP = Crypto("XRP", 6, "✕", Blockchain.Companion.XRP)
        val ADA = Crypto("Cardano", 6, "₳", Blockchain.Companion.ADA)
        val XTZ = Crypto("Tezos", 6, "ꜩ", Blockchain.Companion.XTZ)
        val NMC = Crypto("Namecoin", 8, "ℕ", Blockchain.Companion.NMC)
        val XPM = Crypto("Primecoin", 6, "Ψ", Blockchain.Companion.XPM)
    }
}
package com.tonkeeper.core

data class Blockchain(
    val type: Int,
    val nativeTicker: String,
) {

    val bip44Path: Long
        get() = 0x80000000L + type

    companion object {
        val BTC = Blockchain(0, "BTC")
        val TON = Blockchain(607, "TON")
        val ETH = Blockchain(60, "ETH")
        val ETC = Blockchain(61, "ETC")
        val TRON = Blockchain(195, "TRX")
        val BNB = Blockchain(714, "BNB")
        val SOL = Blockchain(501, "SOL")
        val LTC = Blockchain(2, "LTC")
        val DOGE = Blockchain(3, "DOGE")
        val XMR = Blockchain(128, "XMR")
        val XRP = Blockchain(144, "XRP")
        val ADA = Blockchain(1815, "ADA")
        val XTZ = Blockchain(1729, "XTZ")
        val NMC = Blockchain(7, "NMC")
        val XPM = Blockchain(24, "XPM")
    }
}
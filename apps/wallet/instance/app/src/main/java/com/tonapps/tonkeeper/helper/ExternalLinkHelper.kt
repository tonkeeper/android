package com.tonapps.tonkeeper.helper

object ExternalLinkHelper {

    private fun getTronscanPrefix(testnet: Boolean): String {
        return if (testnet) "https://test.tronscan.org/" else "https://tronscan.org/"
    }

    fun tronToken(walletAddress: String, testnet: Boolean): String {
        val prefix = getTronscanPrefix(testnet)
        return "$prefix#/address/$walletAddress"
    }
}
package com.tonapps.tonkeeper.helper

object ExternalLinkHelper {

    private fun getTonViewerPrefix(testnet: Boolean): String {
        return if (testnet) "https://testnet.tonviewer.com/" else "https://tonviewer.com/"
    }

    private fun getTronscanPrefix(testnet: Boolean): String {
        return if (testnet) "https://test.tronscan.org/" else "https://tronscan.org/"
    }

    fun tonToken(walletAddress: String, tokenAddress: String, testnet: Boolean): String {
        val prefix = getTonViewerPrefix(testnet)
        if (tokenAddress.equals("ton", true)) {
            return "$prefix$walletAddress"
        }
        return "$prefix$walletAddress/jetton/$tokenAddress"
    }

    fun tronToken(walletAddress: String, testnet: Boolean): String {
        val prefix = getTronscanPrefix(testnet)
        return "$prefix#/address/$walletAddress"
    }

    /*
    val detailsUrl = if (token.isTon) {
            "https://tonviewer.com/${screenContext.wallet.address}".toUri()
        } else if (token.isTrc20) {
            "https://tronscan.org/#/address/${viewModel.tronAddress}".toUri()
        } else {
            "https://tonviewer.com/${screenContext.wallet.address}/jetton/${token.address}".toUri()
        }
     */
}
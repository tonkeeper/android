package com.tonapps.tonkeeper.core

object ExternalUrl {

    fun nftMarketView(address: String): String {
        return "https://getgems.io/nft/${address}"
    }

    fun nftExplorerView(address: String): String {
        return "https://tonviewer.com/nft/${address}"
    }
}
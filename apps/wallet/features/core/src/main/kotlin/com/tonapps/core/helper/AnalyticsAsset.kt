package com.tonapps.core.helper

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork

// Legacy to NEW assetId mapper
const val TON_COIN_ASSET_ID = "ton/mainnet/coin"
const val TRON_COIN_ASSET_ID = "tron/mainnet/coin"

private const val TON_NFT_PREFIX = "ton/mainnet/nft/"

fun WalletCurrency.analyticsAssetId(): String {
    return when (val currencyChain = chain) {
        is WalletCurrency.Chain.TON ->
            chainAssetId("ton", "jetton", currencyChain.address, WalletCurrency.TON_CHAIN_KEY)
        is WalletCurrency.Chain.TRON ->
            chainAssetId("tron", "trc20", currencyChain.address, "TRON")
        is WalletCurrency.Chain.ETHEREUM ->
            chainAssetId("eth", "erc20", currencyChain.address, "ETH")
        is WalletCurrency.Chain.ETC ->
            chainAssetId("eth", "erc20", currencyChain.address, "ETC")
        is WalletCurrency.Chain.BNB ->
            chainAssetId("bnb", "bep20", currencyChain.address, "BNB")
        is WalletCurrency.Chain.Solana ->
            chainAssetId("sol", "spl", currencyChain.address, "SOL")
        is WalletCurrency.Chain.BTC -> "btc/mainnet/coin"
        else -> TON_COIN_ASSET_ID
    }
}

private fun chainAssetId(
    chain: String,
    tokenType: String,
    address: String,
    nativeAddress: String,
): String {
    return if (address == nativeAddress) {
        "$chain/mainnet/coin"
    } else {
        "$chain/mainnet/$tokenType/$address"
    }
}

fun TokenEntity.analyticsAssetId(): String {
    return TokenEntity.assetId(blockchain, address, TonNetwork.MAINNET)
}

fun nftAnalyticsAssetId(nftAddress: String): String {
    return TON_NFT_PREFIX + nftAddress
}

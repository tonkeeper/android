package com.tonapps.blockchain.model.account

sealed class Chain( // TODO chain id
    val symbol: String,
    val decimals: Int,
    val network: Network,
    val tokens: List<TokenType>,
    val meta: MetaType,
) {
    object Bitcoin : Chain(
        symbol = "BTC",
        decimals = 8,
        network = Network.Bitcoin.Bitcoin,
        tokens = listOf(TokenType.Brc20),
        meta = MetaType.None,
    )

    object Toncoin : Chain(
        symbol = "TON",
        decimals = 9,
        network = Network.Tvm.Ton,
        tokens = listOf(TokenType.Jetton),
        meta = MetaType.Memo,
    )

    object Trx : Chain(
        symbol = "TRX",
        decimals = 6,
        network = Network.Tron.Tron,
        tokens = listOf(TokenType.Trc20),
        meta = MetaType.Memo,
    )

    object Ethereum : Chain(
        symbol = "ETH",
        decimals = 18,
        network = Network.Evm.Ethereum,
        tokens = listOf(TokenType.Erc20),
        meta = MetaType.Payload,
    )

    object Smartchain : Chain(
        symbol = "BNB",
        decimals = 18,
        network = Network.Evm.Smartchain,
        tokens = listOf(TokenType.Brc20),
        meta = MetaType.Payload,
    )

    object Arbitrum : Chain(
        symbol = "ETH",
        decimals = 18,
        network = Network.Evm.Arbitrum,
        tokens = listOf(TokenType.Erc20),
        meta = MetaType.Payload,
    )

    object Base : Chain(
        symbol = "BNB",
        decimals = 18,
        network = Network.Evm.Base,
        tokens = listOf(TokenType.Erc20),
        meta = MetaType.Payload,
    )
}

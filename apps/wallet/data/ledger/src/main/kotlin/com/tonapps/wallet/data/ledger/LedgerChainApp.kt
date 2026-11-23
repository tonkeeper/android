package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId

enum class LedgerChainApp(val appId: LedgerAppId, val chains: List<Chain>, val isRequired: Boolean) {
    Ton(LedgerAppId.Ton, listOf(Chain.Ton.Mainnet), isRequired = true),
    Ethereum(
        LedgerAppId.Ethereum,
        listOf(
            Chain.Ethereum.Mainnet,
            Chain.Base.Mainnet,
            Chain.Smartchain.Mainnet,
            Chain.Arbitrum.Mainnet,
            Chain.Polygon.Mainnet,
            Chain.Avalanche.Mainnet,
            Chain.Robinhood.Mainnet,
        ),
        isRequired = false,
    ),
    Tron(LedgerAppId.Tron, listOf(Chain.Tron.Mainnet), isRequired = false),
    Bitcoin(LedgerAppId.Bitcoin, listOf(Chain.Bitcoin.Mainnet), isRequired = false),
    Solana(LedgerAppId.Solana, listOf(Chain.Solana.Mainnet), isRequired = false);

    companion object {
        fun forChain(chain: Chain): LedgerChainApp = entries.first { app -> app.chains.any { it == chain } }
    }
}

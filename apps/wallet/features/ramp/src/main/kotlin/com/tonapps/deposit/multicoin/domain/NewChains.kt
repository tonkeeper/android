package com.tonapps.deposit.multicoin.domain

import com.tonapps.chainkit.core.chain.model.account.Chain

// Chains that carry the NEW badge on the "Receiving address" list. Fixed in code: the badge
// stays until a release takes a chain out of here.
internal val NewChains: Set<Chain> = setOf(
    Chain.Solana.Mainnet,
    Chain.Robinhood.Mainnet,
    Chain.Polygon.Mainnet,
    Chain.Avalanche.Mainnet,
)

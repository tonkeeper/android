package com.tonapps.wallet.data.multichain.account

import com.tonapps.chainkit.core.chain.model.account.Chain

private val designOrder: List<Chain> = listOf(
    Chain.Ton.Mainnet,
    Chain.Ethereum.Mainnet,
    Chain.Smartchain.Mainnet,
    Chain.Solana.Mainnet,
    Chain.Tron.Mainnet,
    Chain.Arbitrum.Mainnet,
    Chain.Robinhood.Mainnet,
    Chain.Bitcoin.Mainnet,
    Chain.Polygon.Mainnet,
    Chain.Base.Mainnet,
    Chain.Avalanche.Mainnet,
)

// A chain chainkit adds before the design places it still shows up, after the listed ones.
private val displayOrderList: List<Chain> by lazy {
    designOrder + Chain.all.filterNot { it in designOrder }
}

private val displayOrderIndices: Map<Chain, Int> by lazy {
    displayOrderList.withIndex().associate { (index, chain) -> chain to index }
}

/** Every chain in the order the app lists networks on every screen (TK-4576). */
val Chain.Companion.displayOrder: List<Chain>
    get() = displayOrderList

/** Position of this chain in [displayOrder]; chains outside it go last. */
val Chain.displayOrderIndex: Int
    get() = displayOrderIndices[this] ?: displayOrderIndices.size

/** Compares items by the [displayOrder] of their chain; items of one chain compare equal. */
fun <T> compareByChainDisplayOrder(chain: (T) -> Chain?): Comparator<T> {
    return compareBy { item -> chain(item)?.displayOrderIndex ?: displayOrderIndices.size }
}

/** Orders [this] by the [displayOrder] of each item's chain, keeping the order within one chain. */
fun <T> Iterable<T>.sortedByChainDisplayOrder(chain: (T) -> Chain?): List<T> {
    return sortedWith(compareByChainDisplayOrder(chain))
}

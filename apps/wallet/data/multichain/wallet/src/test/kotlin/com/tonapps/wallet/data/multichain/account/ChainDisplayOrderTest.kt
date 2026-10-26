package com.tonapps.wallet.data.multichain.account

import com.tonapps.chainkit.core.chain.model.account.Chain
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainDisplayOrderTest {

    @Test
    fun `display order follows the design`() {
        assertEquals(
            listOf(
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
            ),
            Chain.displayOrder,
        )
    }

    @Test
    fun `display order holds every chain once`() {
        assertEquals(Chain.all, Chain.displayOrder.toSet())
        assertEquals(Chain.all.size, Chain.displayOrder.size)
    }

    @Test
    fun `display order index matches the position in the order`() {
        Chain.displayOrder.forEachIndexed { index, chain ->
            assertEquals(index, chain.displayOrderIndex)
        }
    }

    @Test
    fun `sorting puts chains in display order`() {
        val shuffled = listOf(
            Chain.Avalanche.Mainnet,
            Chain.Bitcoin.Mainnet,
            Chain.Ton.Mainnet,
            Chain.Solana.Mainnet,
            Chain.Smartchain.Mainnet,
        )

        val sorted = shuffled.sortedByChainDisplayOrder { it }

        assertEquals(
            listOf(
                Chain.Ton.Mainnet,
                Chain.Smartchain.Mainnet,
                Chain.Solana.Mainnet,
                Chain.Bitcoin.Mainnet,
                Chain.Avalanche.Mainnet,
            ),
            sorted,
        )
    }

    @Test
    fun `sorting keeps the order within one chain and puts unknown chains last`() {
        val items = listOf(
            "unknown" to null,
            "usdt-tron" to Chain.Tron.Mainnet,
            "usdt-ton" to Chain.Ton.Mainnet,
            "trx" to Chain.Tron.Mainnet,
            "ton" to Chain.Ton.Mainnet,
        )

        val sorted = items.sortedByChainDisplayOrder { it.second }.map { it.first }

        assertEquals(listOf("usdt-ton", "ton", "usdt-tron", "trx", "unknown"), sorted)
    }

    @Test
    fun `chain order breaks a tie of a primary order without overriding it`() {
        // Home: fiat value first, the network order only among equal values (an empty wallet's zeros).
        val rows = listOf(
            Row("avax", 0, Chain.Avalanche.Mainnet),
            Row("btc", 0, Chain.Bitcoin.Mainnet),
            Row("usdt-tron", 14, Chain.Tron.Mainnet),
            Row("sol", 0, Chain.Solana.Mainnet),
            Row("eth", 0, Chain.Ethereum.Mainnet),
            Row("usdt-ton", 0, Chain.Ton.Mainnet),
            Row("doge-bsc", 5, Chain.Smartchain.Mainnet),
            Row("ton", 0, Chain.Ton.Mainnet),
        )

        val sorted = rows.sortedWith(
            compareByDescending<Row> { it.fiat }.then(compareByChainDisplayOrder { it.chain })
        ).map { it.name }

        assertEquals(
            listOf("usdt-tron", "doge-bsc", "usdt-ton", "ton", "eth", "sol", "btc", "avax"),
            sorted,
        )
    }

    private data class Row(val name: String, val fiat: Int, val chain: Chain)
}

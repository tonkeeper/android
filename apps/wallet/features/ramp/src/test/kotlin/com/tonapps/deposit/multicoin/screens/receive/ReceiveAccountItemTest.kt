package com.tonapps.deposit.multicoin.screens.receive

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.multichain.account.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiveAccountItemTest {

    @Test
    fun `new chains carry the badge`() {
        val chains = listOf(
            Chain.Solana.Mainnet,
            Chain.Robinhood.Mainnet,
            Chain.Polygon.Mainnet,
            Chain.Avalanche.Mainnet,
        )

        val items = chains.map(::account).toReceiveItems()

        assertEquals(chains.map { true }, items.map { it.isNew })
    }

    @Test
    fun `established chains do not`() {
        val chains = listOf(
            Chain.Ton.Mainnet,
            Chain.Ethereum.Mainnet,
            Chain.Smartchain.Mainnet,
            Chain.Tron.Mainnet,
            Chain.Arbitrum.Mainnet,
            Chain.Bitcoin.Mainnet,
            Chain.Base.Mainnet,
        )

        val items = chains.map(::account).toReceiveItems()

        assertEquals(chains.map { false }, items.map { it.isNew })
    }

    @Test
    fun `items keep the accounts and their order`() {
        val accounts = listOf(Chain.Ton.Mainnet, Chain.Solana.Mainnet, Chain.Bitcoin.Mainnet).map(::account)

        val items = accounts.toReceiveItems()

        assertEquals(accounts, items.map { it.account })
        assertEquals(listOf(false, true, false), items.map { it.isNew })
    }

    private fun account(chain: Chain) = AccountEntity(
        walletId = "wallet",
        network = chain.network.type.id,
        mode = chain.network.mode.id,
        displayAddress = "address-${chain.name}",
        publicKey = "",
        segwitPublicKey = "",
    )
}

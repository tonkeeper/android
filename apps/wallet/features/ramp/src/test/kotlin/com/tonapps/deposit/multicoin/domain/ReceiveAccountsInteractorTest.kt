package com.tonapps.deposit.multicoin.domain

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiveAccountsInteractorTest {

    @Test
    fun `accounts follow the network display order, new chains included`() = runBlocking {
        // The order accounts come out of the database in, unrelated to the design.
        val stored = listOf(
            Chain.Avalanche.Mainnet,
            Chain.Ethereum.Mainnet,
            Chain.Bitcoin.Mainnet,
            Chain.Arbitrum.Mainnet,
            Chain.Base.Mainnet,
            Chain.Solana.Mainnet,
            Chain.Smartchain.Mainnet,
            Chain.Polygon.Mainnet,
            Chain.Robinhood.Mainnet,
            Chain.Tron.Mainnet,
            Chain.Ton.Mainnet,
        ).map(::account)

        val chains = interactor(stored).getAccounts().map { it.chain }

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
            chains,
        )
    }

    @Test
    fun `accounts of one chain keep their order`() = runBlocking {
        val stored = listOf(
            account(Chain.Bitcoin.Mainnet, "bc1-segwit"),
            account(Chain.Ton.Mainnet, "ton-v5"),
            account(Chain.Bitcoin.Mainnet, "bc1-taproot"),
            account(Chain.Ton.Mainnet, "ton-v4"),
        )

        val addresses = interactor(stored).getAccounts().map { it.displayAddress }

        assertEquals(listOf("ton-v5", "ton-v4", "bc1-segwit", "bc1-taproot"), addresses)
    }

    private fun interactor(stored: List<AccountEntity>): ReceiveAccountsInteractor {
        val oldAccount = mockk<AccountRepository> {
            every { getSelectedWalletId() } returns WALLET_ID
        }
        val accountRepo = mockk<McAccountRepository> {
            coEvery { getCoinAccounts(WALLET_ID) } returns stored
        }
        return ReceiveAccountsInteractor(oldAccount, accountRepo)
    }

    private fun account(chain: Chain, address: String = chain.network.type.id) = AccountEntity(
        walletId = WALLET_ID,
        network = chain.network.type.id,
        mode = chain.network.mode.id,
        displayAddress = address,
        publicKey = "",
        segwitPublicKey = "",
    )

    private companion object {
        const val WALLET_ID = "wallet"
    }
}

package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.op.OpError
import com.tonapps.wallet.testing.BaseUnitTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binding holds a Solana transfer's mint to the account's asset, so the operation the send screen
 * builds has to name the SPL token it sends.
 */
class ConfirmOperationsTest : BaseUnitTest() {

    private val chain = Chain.Solana.Mainnet

    // Built on first use: an address is validated by the native lib the rule loads.
    private val usdt by lazy {
        Asset.Token(
            id = "sol/mainnet/spl/$USDT_MINT",
            chain = chain,
            contract = Address.force(USDT_MINT, chain),
            type = TokenType.SPL,
            tokenId = "",
            symbol = "USDT",
            name = "Tether USD",
            decimals = Decimal(6),
        )
    }

    @Test
    fun anSplTransferBindsToItsTokenAccount() {
        val operation = ConfirmOperations.transfer(chain, RECIPIENT, BigInteger.fromInt(100_000), token = usdt.contract)

        val bound = operation.transaction(account(usdt))

        assertNull(bound.error)
    }

    @Test
    fun anSplTransferWithoutItsMintDoesNotBind() {
        val operation = ConfirmOperations.transfer(chain, RECIPIENT, BigInteger.fromInt(100_000))

        val bound = operation.transaction(account(usdt))

        assertTrue(bound.error.toString(), bound.error is OpError.Mismatch)
    }

    @Test
    fun aSolTransferBindsToTheCoinAccount() {
        val operation = ConfirmOperations.transfer(chain, RECIPIENT, BigInteger.fromInt(1_000_000))

        val bound = operation.transaction(account(chain.toAsset()))

        assertNull(bound.error)
    }

    private fun account(asset: Asset) = Account.watch(Address.force(OWNER, chain), asset)

    private companion object {
        const val OWNER = "EQAjMWKuR4bf85uanWMJXN2H87f1KrxxVkZMpNaJ9u8T"
        const val RECIPIENT = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
    }
}

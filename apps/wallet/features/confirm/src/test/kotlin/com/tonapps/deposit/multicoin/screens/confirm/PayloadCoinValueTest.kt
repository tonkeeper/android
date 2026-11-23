package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.wallet.testing.BaseUnitTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The coin a payload moves out of the wallet: what the balance check and a whole balance's send read.
 * A Solana payment moves its lamports; an SPL transfer moves the token, not the coin.
 */
class PayloadCoinValueTest : BaseUnitTest() {

    private val chain = Chain.Solana.Mainnet

    @Test
    fun aSolPaymentMovesItsLamports() {
        val meta = PayloadMeta.Solana.Main(to = Address.force(RECIPIENT, chain), value = LAMPORTS)

        assertEquals(LAMPORTS, meta.coinValue())
    }

    @Test
    fun anSplTransferMovesNoLamports() {
        val meta = PayloadMeta.Solana.Main(
            to = Address.force(RECIPIENT, chain),
            value = LAMPORTS,
            token = Address.force(USDT_MINT, chain),
        )

        assertEquals(BigInteger.ZERO, meta.coinValue())
    }

    private companion object {
        const val RECIPIENT = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        val LAMPORTS: BigInteger = BigInteger.fromInt(1_000_000)
    }
}

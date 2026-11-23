package com.tonapps.blockchain.solana

import com.trustwallet.core.WalletCoreLibLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.util.Base64

class SolanaTransactionTest {

    @Test
    fun aLegacyTransactionIsPaidByItsFirstKey() {
        val transaction = transaction(versioned = false, payer = key(7))

        assertEquals(PAYER_7, SolanaTransaction.feePayer(transaction))
    }

    @Test
    fun aVersionedTransactionIsPaidByItsFirstKey() {
        val transaction = transaction(versioned = true, payer = key(7))

        assertEquals(PAYER_7, SolanaTransaction.feePayer(transaction))
    }

    @Test
    fun whatIsNotATransactionHasNoFeePayer() {
        assertNull(SolanaTransaction.feePayer("not base64!"))
        assertNull(SolanaTransaction.feePayer(Base64.getEncoder().encodeToString(byteArrayOf(2, 0, 0))))
        assertNull(SolanaTransaction.feePayer(""))
    }

    private fun key(last: Int) = ByteArray(32).also { it[31] = last.toByte() }

    // One signature slot, a message header (one signer, one read-only unsigned key), two account
    // keys (the payer first), the blockhash, no instructions and, for a versioned message, no
    // lookup tables: a message ChainKit's decoder accepts as a whole.
    private fun transaction(versioned: Boolean, payer: ByteArray): String {
        val bytes = buildList {
            add(1)
            repeat(64) { add(0) }
            if (versioned) {
                add(0x80)
            }
            addAll(listOf(1, 0, 1))
            add(2)
            addAll(payer.map(Byte::toInt))
            addAll(key(9).map(Byte::toInt))
            repeat(32) { add(0) }
            add(0)
            if (versioned) {
                add(0)
            }
        }.map(Int::toByte).toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        // 31 zero bytes and a 7, base58.
        private const val PAYER_7 = "1111111111111111111111111111111" + "8"

        @JvmStatic
        @BeforeClass
        fun loadWalletCore() {
            WalletCoreLibLoader.loadLibrary()
        }
    }
}

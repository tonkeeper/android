package com.tonapps.wallet.data.ledger

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerLegacyTwinsTest {

    private fun wallet(id: String, publicKeyHex: String, isLedger: Boolean = true, testnet: Boolean = false) = mockk<WalletEntity> {
        every { this@mockk.id } returns id
        every { this@mockk.isLedger } returns isLedger
        every { this@mockk.testnet } returns testnet
        every { publicKey } returns publicKeyHex.publicKeyFromHex()
    }

    @Test
    fun `a legacy ledger wallet with the same ton key is the twin whatever the hex case and prefix`() {
        val wallets = listOf(wallet("software", KEY_A, isLedger = false), wallet("ledger", KEY_A))
        assertEquals("ledger", LedgerLegacyTwins.find(wallets, "0x" + KEY_A.uppercase())?.id)
    }

    @Test
    fun `testnet ledger wallets and other keys are not twins`() {
        val wallets = listOf(wallet("testnet", KEY_A, testnet = true), wallet("other", KEY_B))
        assertNull(LedgerLegacyTwins.find(wallets, KEY_A))
    }

    private companion object {
        const val KEY_A = "0b1f4a8e6c2d9e7f30a5b4c3d2e1f0112233445566778899aabbccddeeff0011"
        const val KEY_B = "ffeeddccbbaa99887766554433221100f0e1d2c3b4a5968778695a4b3c2d1e0f"
    }
}

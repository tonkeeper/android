package com.tonapps.tonkeeper.ui.screen.wallet.picker

import com.tonapps.blockchain.model.legacy.WalletEntity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerModeTest {

    private val tonWallet = wallet(isMultichain = false, supportsPerps = false)
    private val multichainWallet = wallet(isMultichain = true, supportsPerps = true)
    private val multichainLedger = wallet(isMultichain = true, supportsPerps = false)

    @Test
    fun multichainPickerAcceptsEveryMultichainWallet() {
        val mode = PickerMode.Multichain("tonkeeper://dapp/https://example.com")

        assertTrue(mode.accepts(multichainWallet))
        assertTrue(mode.accepts(multichainLedger))
        assertFalse(mode.accepts(tonWallet))
    }

    @Test
    fun perpsPickerAcceptsOnlyWalletsThatTradePerps() {
        val mode = PickerMode.Multichain("tonkeeper://perps", perpsOnly = true)

        assertTrue(mode.accepts(multichainWallet))
        assertFalse(mode.accepts(multichainLedger))
        assertFalse(mode.accepts(tonWallet))
    }

    @Test
    fun subtitleDoesNotNarrowTheMultichainWallets() {
        val mode = PickerMode.Multichain(
            "tonkeeper://dapp/https://loyalty.keeperwallet.com/referrals?referralCode=K7M2QX9P",
            subtitleResId = 1,
        )

        assertTrue(mode.accepts(multichainWallet))
        assertTrue(mode.accepts(multichainLedger))
        assertFalse(mode.accepts(tonWallet))
    }

    private fun wallet(isMultichain: Boolean, supportsPerps: Boolean): WalletEntity = mockk {
        every { this@mockk.isMultichain } returns isMultichain
        every { this@mockk.supportsPerps } returns supportsPerps
    }
}

package com.tonapps.tonkeeper.ui.screen.wallet.main

import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel.Companion.findAdjacentWalletId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletViewModelAdjacentWalletTest {

    private val walletIds = listOf("a", "b", "c")

    @Test
    fun nextReturnsFollowingWallet() {
        assertEquals("b", findAdjacentWalletId(walletIds, "a", offset = 1))
    }

    @Test
    fun nextWrapsFromLastToFirst() {
        assertEquals("a", findAdjacentWalletId(walletIds, "c", offset = 1))
    }

    @Test
    fun prevReturnsPrecedingWallet() {
        assertEquals("b", findAdjacentWalletId(walletIds, "c", offset = -1))
    }

    @Test
    fun prevWrapsFromFirstToLast() {
        assertEquals("c", findAdjacentWalletId(walletIds, "a", offset = -1))
    }

    @Test
    fun returnsNullWhenCurrentWalletIsMissing() {
        assertNull(findAdjacentWalletId(walletIds, "removed", offset = -1))
        assertNull(findAdjacentWalletId(walletIds, "removed", offset = 1))
    }

    @Test
    fun returnsNullForSingleWallet() {
        assertNull(findAdjacentWalletId(listOf("a"), "a", offset = -1))
        assertNull(findAdjacentWalletId(listOf("a"), "a", offset = 1))
    }

    @Test
    fun returnsNullForNoWallets() {
        assertNull(findAdjacentWalletId(emptyList(), "a", offset = 1))
    }
}

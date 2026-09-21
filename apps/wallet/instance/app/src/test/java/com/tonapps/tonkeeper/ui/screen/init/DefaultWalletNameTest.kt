package com.tonapps.tonkeeper.ui.screen.init

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultWalletNameTest {

    @Test
    fun returnsBaseWhenNoWalletsExist() {
        assertEquals("Wallet", suggest())
    }

    @Test
    fun returnsBaseWhenOnlyCustomNamesExist() {
        assertEquals("Wallet", suggest("Savings", "Trading 2", "My Wallet 3"))
    }

    @Test
    fun returnsSecondIndexWhenBaseIsTaken() {
        assertEquals("Wallet 2", suggest("Wallet"))
    }

    @Test
    fun continuesFromHighestIndex() {
        assertEquals("Wallet 4", suggest("Wallet", "Wallet 2", "Wallet 3"))
    }

    @Test
    fun doesNotReuseFreedIndexes() {
        assertEquals("Wallet 6", suggest("Wallet", "Wallet 5"))
        assertEquals("Wallet 5", suggest("Wallet 4"))
    }

    @Test
    fun ignoresCaseAndSurroundingWhitespace() {
        assertEquals("Wallet 3", suggest(" wallet ", "WALLET 2"))
    }

    @Test
    fun ignoresLabelsWithNonIndexSuffix() {
        assertEquals("Wallet", suggest("Wallet v4R2", "Wallet 2x", "Wallet 1.5", "Wallet 007"))
    }

    @Test
    fun ignoresNonAsciiDigitSuffix() {
        assertEquals("Wallet", suggest("Wallet ٢"))
    }

    @Test
    fun trimsBaseBeforeMatching() {
        assertEquals("Wallet 2", DefaultWalletName.suggest(" Wallet ", listOf("Wallet")))
    }

    @Test
    fun returnsBlankBaseAsIs() {
        assertEquals("", DefaultWalletName.suggest("", listOf("Wallet")))
        assertEquals("   ", DefaultWalletName.suggest("   ", listOf("Wallet")))
    }

    @Test
    fun ignoresIndexesThatOverflowInt() {
        assertEquals("Wallet", suggest("Wallet 9999999999"))
    }

    @Test
    fun doesNotIncrementPastMaxInt() {
        assertEquals("Wallet", suggest("Wallet ${Int.MAX_VALUE}"))
    }

    private fun suggest(vararg existingLabels: String): String =
        DefaultWalletName.suggest(base = "Wallet", existingLabels = existingLabels.toList())
}

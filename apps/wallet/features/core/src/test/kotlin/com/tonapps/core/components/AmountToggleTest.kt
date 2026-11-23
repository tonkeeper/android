package com.tonapps.core.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountToggleTest {

    private val toggledToFiat = AmountToggle(assetId = TON, sourceText = "0.3", toggledText = "0.35", toFiat = true)

    @Test
    fun togglingStraightBackRestoresTheSourceText() {
        val back = toggledToFiat.toggledBack(TON, text = "0.35", toFiat = false)

        assertEquals(AmountToggle(assetId = TON, sourceText = "0.35", toggledText = "0.3", toFiat = false), back)
    }

    @Test
    fun alternatingTogglesKeepBothTexts() {
        val back = toggledToFiat.toggledBack(TON, text = "0.35", toFiat = false)!!
        val again = back.toggledBack(TON, text = "0.3", toFiat = true)

        assertEquals(toggledToFiat, again)
    }

    @Test
    fun togglingBackRestoresMax() {
        val fromMax = toggledToFiat.copy(sourceIsMax = true)

        val back = fromMax.toggledBack(TON, text = "0.35", toFiat = false)!!
        assertTrue(back.toggledIsMax)

        val again = back.toggledBack(TON, text = "0.3", toFiat = true, isMax = true)!!
        assertFalse(again.toggledIsMax)
        assertTrue(again.sourceIsMax)
    }

    @Test
    fun maxClearedBeforeTheNextToggleStaysCleared() {
        val back = toggledToFiat.copy(sourceIsMax = true).toggledBack(TON, text = "0.35", toFiat = false)!!

        val again = back.toggledBack(TON, text = "0.3", toFiat = true, isMax = false)!!
        val backAgain = again.toggledBack(TON, text = "0.35", toFiat = false)!!

        assertFalse(backAgain.toggledIsMax)
    }

    @Test
    fun anEditedAmountIsConvertedAgain() {
        assertNull(toggledToFiat.toggledBack(TON, text = "0.4", toFiat = false))
    }

    @Test
    fun anotherAssetIsConvertedAgain() {
        assertNull(toggledToFiat.toggledBack("usdt", text = "0.35", toFiat = false))
    }

    @Test
    fun aFieldThatLeftTheToggledCurrencyIsConvertedAgain() {
        assertNull(toggledToFiat.toggledBack(TON, text = "0.35", toFiat = true))
    }

    private companion object {
        const val TON = "ton/mainnet/coin"
    }
}

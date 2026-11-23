package com.tonapps.deposit.multicoin.screens.buy

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class BuyCryptoFormatTest {

    private val grouping = GroupedAmountTransformation(separator = " ", color = null)

    @Test
    fun `a fiat sum is tight to its symbol, a code keeps its space`() {
        assertEquals("$50", tightFiat("$ 50"))
        assertEquals("50€", tightFiat("50 €"))
        assertEquals("50 PLN", tightFiat("50 PLN"))
        assertEquals("$1,500.5", tightFiat("$ 1,500.5"))
    }

    @Test
    fun `an ICU amount takes the wallet total's grouping`() {
        assertEquals("1 875.5", regroup("1,875.5", from = ",", to = " "))
        assertEquals("1 875,5", regroup("1.875,5", from = ".", to = " "))
        assertEquals("1,875", regroup("1,875", from = "", to = " "))
        assertEquals("Bs.1 875", regroup("Bs.1.875", from = ".", to = " "))
    }

    @Test
    fun `the integer part is grouped by thousands, the fraction is not`() {
        assertEquals("2 008", shown("2008"))
        assertEquals("1 234 567.1234", shown("1234567.1234"))
        assertEquals("12 345,6", shown("12345,6"))
        assertEquals("999", shown("999"))
        assertEquals("", shown(""))
        assertEquals("0.", shown("0."))
    }

    @Test
    fun `the cursor keeps its place among the digits`() {
        val mapping = grouping.filter(AnnotatedString("1234567")).offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(6, mapping.originalToTransformed(4))
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(1, mapping.transformedToOriginal(1))
        assertEquals(1, mapping.transformedToOriginal(2))
        assertEquals(4, mapping.transformedToOriginal(6))
        assertEquals(7, mapping.transformedToOriginal(9))
    }

    private fun shown(text: String): String = grouping.filter(AnnotatedString(text)).text.text
}

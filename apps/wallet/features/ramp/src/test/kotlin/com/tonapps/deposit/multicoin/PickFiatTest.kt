package com.tonapps.deposit.multicoin

import org.junit.Assert.assertEquals
import org.junit.Test

class PickFiatTest {

    @Test
    fun `the preferred fiat wins when the asset supports it`() {
        assertEquals("EUR", pickFiat(supported = listOf("USD", "EUR"), known = listOf("USD", "EUR", "RUB"), preferred = "eur"))
    }

    @Test
    fun `an unsupported preferred fiat falls back to USD`() {
        assertEquals("USD", pickFiat(supported = listOf("EUR", "USD"), known = listOf("EUR", "USD", "RUB"), preferred = "RUB"))
    }

    @Test
    fun `without USD the first supported fiat of the app's list is taken`() {
        assertEquals("GBP", pickFiat(supported = listOf("EUR", "GBP"), known = listOf("GBP", "EUR"), preferred = "RUB"))
    }

    @Test
    fun `a fiat the app does not know is never picked`() {
        assertEquals("EUR", pickFiat(supported = listOf("XYZ", "EUR"), known = listOf("EUR"), preferred = "XYZ"))
    }

    @Test
    fun `without the app's list every supported fiat counts`() {
        assertEquals("XYZ", pickFiat(supported = listOf("XYZ", "EUR"), known = emptyList(), preferred = "XYZ"))
    }

    @Test
    fun `an asset without fiats still gets USD`() {
        assertEquals("USD", pickFiat(supported = emptyList(), known = listOf("EUR"), preferred = "EUR"))
    }
}

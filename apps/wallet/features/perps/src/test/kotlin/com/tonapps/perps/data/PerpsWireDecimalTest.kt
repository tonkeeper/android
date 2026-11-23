package com.tonapps.perps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PerpsWireDecimalTest {

    @Test
    fun backendDecimalsParseAsBefore() {
        assertEquals(BigDecimal("80052.5"), "80052.5".toWireDecimalOrNull())
        assertEquals(BigDecimal("-0.00024"), "-0.00024".toWireDecimalOrNull())
        assertEquals(BigDecimal("0.0000125"), "0.0000125".toWireDecimalOrNull())
        assertEquals(BigDecimal("1.2345678901234567E-20"), "1.2345678901234567E-20".toWireDecimalOrNull())
        assertEquals(BigDecimal("1E+64"), "1e64".toWireDecimalOrNull())
        assertEquals(BigDecimal("1E-64"), "1e-64".toWireDecimalOrNull())
        assertEquals(BigDecimal("9".repeat(64)), "9".repeat(64).toWireDecimalOrNull())
    }

    @Test
    fun extremeExponentsAndOversizedStringsAreRejected() {
        val rejected = listOf(
            "1e999999999",
            "1E-999999999",
            "1e2147483648",
            "1e65",
            "1e-65",
            "9".repeat(65),
            "0." + "0".repeat(70) + "1",
            "NaN",
            "Infinity",
            "",
            " ",
            "12 5",
            null,
        )
        for (raw in rejected) {
            assertNull(raw, raw.toWireDecimalOrNull())
        }
    }

    @Test
    fun onlyFiniteDoublesBecomeDecimals() {
        assertEquals(BigDecimal.valueOf(1.5), 1.5.toFiniteBigDecimalOrNull())
        assertNull(Double.NaN.toFiniteBigDecimalOrNull())
        assertNull(Double.POSITIVE_INFINITY.toFiniteBigDecimalOrNull())
        assertNull(Double.NEGATIVE_INFINITY.toFiniteBigDecimalOrNull())
    }
}

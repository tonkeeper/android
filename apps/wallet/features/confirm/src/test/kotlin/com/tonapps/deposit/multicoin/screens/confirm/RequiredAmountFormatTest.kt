package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.chainkit.core.chain.model.num.Formatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class RequiredAmountFormatTest {

    private val group = Formatter.defaultOptions.groupingSeparator
    private val point = Formatter.defaultOptions.decimalSeparator

    @Test
    fun theRequiredAmountIsRoundedUpNeverDown() {
        assertEquals("1${point}0312346", formatRequiredAmount(BigDecimal("1.031234567")))
        assertEquals("0${point}000600001", formatRequiredAmount(BigDecimal("0.000600000000000001")))
    }

    @Test
    fun aLargeRequiredAmountKeepsItsWholePartAndIsGrouped() {
        assertEquals("1${group}234${group}567${point}89", formatRequiredAmount(BigDecimal("1234567.89")))
    }

    @Test
    fun anAmountWithFewDigitsIsShownAsItIs() {
        assertEquals("1${point}01", formatRequiredAmount(BigDecimal("1.01")))
        assertEquals("100", formatRequiredAmount(BigDecimal("100")))
    }
}

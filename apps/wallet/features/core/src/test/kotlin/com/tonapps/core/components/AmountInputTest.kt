package com.tonapps.core.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun keepsLatinInputUnchanged() {
        assertEquals("", "".normalizeAmountInput())
        assertEquals("123", "123".normalizeAmountInput())
        assertEquals("0.05", "0.05".normalizeAmountInput())
        assertEquals("0,05", "0,05".normalizeAmountInput())
    }

    @Test
    fun normalizesArabicIndicDigits() {
        assertEquals("123", "١٢٣".normalizeAmountInput())
    }

    @Test
    fun normalizesExtendedArabicIndicDigits() {
        assertEquals("1234567890", "۱۲۳۴۵۶۷۸۹۰".normalizeAmountInput())
    }

    @Test
    fun normalizesOtherNonLatinDigits() {
        assertEquals("123", "१२३".normalizeAmountInput())
        assertEquals("123", "১২৩".normalizeAmountInput())
        assertEquals("123", "１２３".normalizeAmountInput())
    }

    @Test
    fun normalizesMixedLatinAndNonLatinDigits() {
        assertEquals("123", "1۲3".normalizeAmountInput())
    }

    @Test
    fun normalizesNonLatinDecimalSeparators() {
        assertEquals("1.5", "۱٫۵".normalizeAmountInput())
        assertEquals("1.5", "１．５".normalizeAmountInput())
    }

    @Test
    fun normalizesDigitsOutsideTheBasicPlane() {
        assertEquals("123", "𝟏𝟐𝟑".normalizeAmountInput())
        assertEquals("1.5", "𝟏.𝟓".normalizeAmountInput())
    }

    @Test
    fun keepsUnknownCharactersForTheCaller() {
        assertEquals("1٬000", "۱٬۰۰۰".normalizeAmountInput())
        assertEquals("12abc", "۱۲abc".normalizeAmountInput())
        assertEquals("1😀", "۱😀".normalizeAmountInput())
    }

    @Test
    fun sanitizeKeepsLatinInputUnchanged() {
        assertEquals("", "".sanitizeAmountInput())
        assertEquals("123", "123".sanitizeAmountInput())
        assertEquals("0.05", "0.05".sanitizeAmountInput())
        assertEquals("0,05", "0,05".sanitizeAmountInput())
    }

    @Test
    fun sanitizeNormalizesNonLatinInput() {
        assertEquals("123", "١٢٣".sanitizeAmountInput())
        assertEquals("1.5", "۱٫۵".sanitizeAmountInput())
        assertEquals("1.5", "１．５".sanitizeAmountInput())
        assertEquals("123", "𝟏𝟐𝟑".sanitizeAmountInput())
    }

    @Test
    fun sanitizeDropsEverythingButDigitsAndSeparators() {
        assertEquals("12", "۱۲abc".sanitizeAmountInput())
        assertEquals("1", "۱😀".sanitizeAmountInput())
        assertEquals("1000", "۱٬۰۰۰".sanitizeAmountInput())
        assertEquals("1000", "1 000".sanitizeAmountInput())
        assertEquals("", "abc".sanitizeAmountInput())
        assertEquals("5", "-5".sanitizeAmountInput())
    }

    @Test
    fun sanitizeKeepsOnlyTheFirstSeparator() {
        assertEquals("1.23", "1.2.3".sanitizeAmountInput())
        assertEquals("1,23", "1,2,3".sanitizeAmountInput())
        assertEquals("1.23", "1.2,3".sanitizeAmountInput())
        assertEquals("1.55", "۱٫۵٫۵".sanitizeAmountInput())
    }

    @Test
    fun sanitizePrefixesLeadingSeparatorWithZero() {
        assertEquals("0.5", ".5".sanitizeAmountInput())
        assertEquals("0,5", ",5".sanitizeAmountInput())
        assertEquals("0.5", "٫۵".sanitizeAmountInput())
        assertEquals("0.", ".".sanitizeAmountInput())
    }

    @Test
    fun sanitizeCapsDecimals() {
        assertEquals("1.23", "1.23456".sanitizeAmountInput(maxDecimals = 2))
        assertEquals("1,23", "1,23456".sanitizeAmountInput(maxDecimals = 2))
        assertEquals("1.", "1.5".sanitizeAmountInput(maxDecimals = 0))
        assertEquals("123.45", "۱۲۳٫۴۵۶".sanitizeAmountInput(maxDecimals = 2))
        assertEquals("1.23456789", "1.23456789".sanitizeAmountInput(maxDecimals = 9))
    }
}

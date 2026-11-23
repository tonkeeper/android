package com.tonapps.deposit.multicoin.moonpay

import org.junit.Assert.assertEquals
import org.junit.Test

class MoonPayMethodSupportTest {

    @Test
    fun `Google Pay needs both the flag and the device, Apple Pay never shows`() {
        val cases = listOf(true to true, true to false, false to true, false to false)
        val googlePay = cases.map { (flag, device) -> MoonPayMethodSupport({ flag }, { device }).supports(MOONPAY_GOOGLE_PAY_TYPE) }
        val applePay = cases.map { (flag, device) -> MoonPayMethodSupport({ flag }, { device }).supports(MOONPAY_APPLE_PAY_TYPE) }
        val card = cases.map { (flag, device) -> MoonPayMethodSupport({ flag }, { device }).supports("card") }

        assertEquals(listOf(true, false, false, false), googlePay)
        assertEquals(listOf(false, false, false, false), applePay)
        assertEquals(listOf(true, true, true, true), card)
    }
}

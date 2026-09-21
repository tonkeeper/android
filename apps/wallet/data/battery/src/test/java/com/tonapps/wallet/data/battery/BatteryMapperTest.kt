package com.tonapps.wallet.data.battery

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BatteryMapperTest {

    @Test
    fun positiveRemainderCountsAsWholeCharge() {
        assertEquals(4, signedCharges("3.2"))
        assertEquals(1, signedCharges("0.1"))
        assertEquals(3, signedCharges("3"))
    }

    @Test
    fun negativeRemainderRoundsAwayFromZero() {
        assertEquals(-4, signedCharges("-3.2"))
        assertEquals(-1, signedCharges("-0.1"))
        assertEquals(-126, signedCharges("-126"))
    }

    @Test
    fun zeroStaysZero() {
        assertEquals(0, signedCharges("0"))
    }

    @Test
    fun spendableChargesClampNegativeToZero() {
        assertEquals(0, BatteryMapper.convertToCharges(coins("-3.2"), "1"))
        assertEquals(4, BatteryMapper.convertToCharges(coins("3.2"), "1"))
    }

    @Test
    fun singleStoreRefundKeepsIapEnabled() {
        val purchases = listOf(
            purchase(1, store = true, refunded = true),
            purchase(2, store = true, refunded = false),
        )
        assertFalse(BatteryMapper.isIapDisabledByRefunds(purchases))
    }

    @Test
    fun secondStoreRefundDisablesIap() {
        val purchases = listOf(
            purchase(1, store = true, refunded = true),
            purchase(2, store = true, refunded = true),
            purchase(3, store = false, refunded = false),
        )
        assertTrue(BatteryMapper.isIapDisabledByRefunds(purchases))
    }

    @Test
    fun nonStoreRefundsDoNotDisableIap() {
        val purchases = listOf(
            purchase(1, store = false, refunded = true),
            purchase(2, store = false, refunded = true),
            purchase(3, store = false, refunded = true),
        )
        assertFalse(BatteryMapper.isIapDisabledByRefunds(purchases))
    }

    @Test
    fun emptyPurchasesKeepIapEnabled() {
        assertFalse(BatteryMapper.isIapDisabledByRefunds(emptyList()))
    }

    private fun signedCharges(balance: String) = BatteryMapper.convertToSignedCharges(coins(balance), "1")

    private fun coins(value: String) = Coins.of(BigDecimal(value), 20)

    private fun purchase(id: Int, store: Boolean, refunded: Boolean) = BatteryPurchaseEntity(
        id = id,
        isStorePurchase = store,
        isRefunded = refunded,
    )
}

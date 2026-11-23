package com.tonapps.wallet.data.battery

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
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

    @Test
    fun iapChargesFollowTheTonRate() {
        assertEquals(793, iapCharges(userProceed = 3.9655))
        assertEquals(1586, iapCharges(userProceed = 7.931))
        assertEquals(2379, iapCharges(userProceed = 11.8965))
    }

    @Test
    fun iapChargesRoundHalfUpLikeIos() {
        assertEquals(794, iapCharges(userProceed = 3.9675))
        assertEquals(793, iapCharges(userProceed = 3.96749))
    }

    @Test
    fun emptyBatterySubtractsReservedAmountFromIapCharges() {
        assertEquals(767, iapCharges(userProceed = 3.9655, reservedAmount = "0.065"))
    }

    @Test
    fun reservedAmountAbovePurchaseYieldsZeroIapCharges() {
        assertEquals(0, iapCharges(userProceed = 0.1, reservedAmount = "0.065"))
    }

    @Test
    fun missingRateOrFeeYieldsZeroIapCharges() {
        assertEquals(0, iapCharges(userProceed = 3.9655, tonPriceInUsd = "0"))
        assertEquals(0, iapCharges(userProceed = 3.9655, meanFees = "0"))
    }

    private fun iapCharges(
        userProceed: Double,
        tonPriceInUsd: String = "2",
        reservedAmount: String = "0",
        meanFees: String = "0.0025",
    ) = BatteryMapper.calculateIapCharges(
        userProceed = userProceed,
        tonPriceInUsd = coins(tonPriceInUsd),
        reservedAmount = BigDecimal(reservedAmount),
        meanFees = BigDecimal(meanFees),
    )

    @Test
    fun cryptoChargesFollowTheChargesRate() {
        // 10 USD₮ at 335.7457 charges each; the rate alone would give 0.87293889 / 0.0026 = 335.745…
        assertEquals(3357, BatteryMapper.calculateCryptoCharges(usdt(chargesRate = "335.7457"), "0.0026", coins("10")))
    }

    @Test
    fun cryptoChargesFallBackToTheRateWithoutAChargesRate() {
        // 0.0052 TON a unit at 0.0026 TON a charge: two charges a unit.
        assertEquals(20, BatteryMapper.calculateCryptoCharges(usdt(rate = "0.0052", chargesRate = null), "0.0026", coins("10")))
    }

    @Test
    fun aPackCostsItsChargesOverTheChargesRate() {
        // 750 / 335.7457 = 2.2338335… USD₮, up to six decimals.
        assertEquals(BigDecimal("2.233834"), usdt(chargesRate = "335.7457").amountForCharges(750, "0.0026"))
    }

    private fun usdt(rate: String = "0.87293889", chargesRate: String?) = RechargeMethodEntity(
        type = RechargeMethodType.JETTON,
        rate = rate,
        symbol = "USD₮",
        decimals = 6,
        supportGasless = true,
        supportRecharge = true,
        chargesRate = chargesRate,
    )

    private fun signedCharges(balance: String) = BatteryMapper.convertToSignedCharges(coins(balance), "1")

    private fun coins(value: String) = Coins.of(BigDecimal(value), 20)

    private fun purchase(id: Int, store: Boolean, refunded: Boolean) = BatteryPurchaseEntity(
        id = id,
        isStorePurchase = store,
        isRefunded = refunded,
    )
}

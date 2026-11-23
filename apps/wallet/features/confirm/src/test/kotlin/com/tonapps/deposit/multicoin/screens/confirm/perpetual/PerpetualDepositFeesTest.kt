package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.ConfirmType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerpetualDepositFeesTest {

    private val providerFees = ConfirmType.PerpetualDeposit.ProviderFees(
        destinationGasUsd = BigDecimal.parseString("0.6"),
        providerUsd = BigDecimal.parseString("0.37"),
    )

    @Test
    fun theTotalAddsTheSourceNetworkFeeToEveryProviderFee() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "GRAM",
            networkFeeUsd = BigDecimal.parseString("0.03"),
            providerFees = providerFees,
            spentUsd = BigDecimal.parseString("10"),
        )

        val fees = requireNotNull(pricing.fees)
        assertEquals("≈ $ 1", fees.total)
        assertEquals("≈ $ 0.63", fees.network)
        assertEquals("≈ $ 0.37", fees.provider)
        assertEquals("GRAM", fees.symbol)
        assertEquals(1_000, pricing.feeShareBps)
    }

    @Test
    fun aDepositWithoutProviderFeesShowsAZeroProviderFee() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "ETH",
            networkFeeUsd = BigDecimal.parseString("0.16"),
            providerFees = ConfirmType.PerpetualDeposit.ProviderFees(BigDecimal.ZERO, BigDecimal.ZERO),
            spentUsd = BigDecimal.parseString("1.06"),
        )

        assertEquals("≈ $ 0.16", pricing.fees?.total)
        assertEquals("≈ $ 0.16", pricing.fees?.network)
        assertEquals("≈ $ 0", pricing.fees?.provider)
        assertEquals(1_509, pricing.feeShareBps)
    }

    @Test
    fun anUnpricedNetworkFeeKeepsThePlainNetworkFeeRowButCountsTheKnownFees() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "GRAM",
            networkFeeUsd = null,
            providerFees = providerFees,
            spentUsd = BigDecimal.parseString("5"),
        )

        assertNull(pricing.fees)
        assertEquals(1_940, pricing.feeShareBps)
    }

    @Test
    fun unreadableProviderFeesKeepThePlainNetworkFeeRowButCountTheNetworkFee() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "GRAM",
            networkFeeUsd = BigDecimal.parseString("0.5"),
            providerFees = null,
            spentUsd = BigDecimal.parseString("2"),
        )

        assertNull(pricing.fees)
        assertEquals(2_500, pricing.feeShareBps)
    }

    @Test
    fun theDeliveryFeesLeaveTheSourceNetworkFeeOut() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "Battery",
            networkFeeUsd = null,
            providerFees = providerFees,
            spentUsd = BigDecimal.parseString("10"),
        )

        val delivery = requireNotNull(pricing.delivery)
        assertEquals("≈ $ 0.97", delivery.total)
        assertEquals("≈ $ 0.6", delivery.network)
        assertEquals("≈ $ 0.37", delivery.provider)
    }

    @Test
    fun aDepositWithNothingToPayTheProviderHasNoDeliveryFees() {
        val free = perpetualDepositPricing(
            feeSymbol = "Battery",
            networkFeeUsd = BigDecimal.parseString("0.1"),
            providerFees = ConfirmType.PerpetualDeposit.ProviderFees(BigDecimal.ZERO, BigDecimal.ZERO),
            spentUsd = BigDecimal.parseString("10"),
        )
        val unread = perpetualDepositPricing(
            feeSymbol = "Battery",
            networkFeeUsd = BigDecimal.parseString("0.1"),
            providerFees = null,
            spentUsd = BigDecimal.parseString("10"),
        )

        assertNull(free.delivery)
        assertNull(unread.delivery)
    }

    @Test
    fun anUnpricedSourceGivesNoShare() {
        val pricing = perpetualDepositPricing(
            feeSymbol = "GRAM",
            networkFeeUsd = BigDecimal.parseString("0.03"),
            providerFees = providerFees,
            spentUsd = null,
        )

        assertNotNull(pricing.fees)
        assertNull(pricing.feeShareBps)
    }
}

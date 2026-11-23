package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampQuotes
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangeMerchantSlug.mercuryo
import io.exchangeapi.models.ExchangeMerchantSlug.moonpay
import io.exchangeapi.models.ExchangeMerchantSlug.transak
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.RampFees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RampLimitsTest {

    @Test
    fun `a quote's limits win over the provider's`() {
        val limits = limits(
            providers = listOf(provider(mercuryo, min = "10", max = "1000")),
            quotes = quotes(items = listOf(quote(mercuryo, min = "30", max = "500"))),
        )

        assertEquals(RampLimits.Range(BigDecimal("30"), BigDecimal("500")), limits.limits(mercuryo))
    }

    @Test
    fun `a quote's zero minimum lifts the provider's`() {
        val limits = limits(
            providers = listOf(provider(mercuryo, min = "20")),
            quotes = quotes(items = listOf(quote(mercuryo, min = "0"))),
        )

        assertTrue(limits.canServe(mercuryo, BigDecimal("10")))
    }

    @Test
    fun `zero and missing limits are no limits`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "0", max = null)))

        assertNull(limits.limits(mercuryo))
        assertTrue(limits.canServe(mercuryo, BigDecimal("1")))
    }

    @Test
    fun `limits are compared at the fiat's precision`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "10.005", max = "99.991")))

        assertTrue(limits.canServe(mercuryo, BigDecimal("10.00")))
        assertTrue(limits.canServe(mercuryo, BigDecimal("100.00")))
        assertFalse(limits.canServe(mercuryo, BigDecimal("9.99")))
        assertFalse(limits.canServe(mercuryo, BigDecimal("100.01")))
    }

    @Test
    fun `the best merchant is the first in the server's order that takes the amount`() {
        val limits = limits(
            providers = listOf(provider(mercuryo), provider(transak), provider(moonpay)),
            quotes = quotes(
                items = listOf(quote(transak, min = "100"), quote(moonpay)),
                suggested = listOf(quote(mercuryo)),
            ),
        )

        assertEquals(listOf(transak, moonpay, mercuryo), limits.orderedMerchants)
        assertEquals(moonpay, limits.bestMerchant(BigDecimal("50")))
    }

    @Test
    fun `without quotes the providers' order is kept`() {
        val limits = limits(providers = listOf(provider(transak), provider(mercuryo), provider(transak)))

        assertEquals(listOf(transak, mercuryo), limits.orderedMerchants)
    }

    @Test
    fun `merchants of another payment method or fiat are left out`() {
        val limits = limits(
            providers = listOf(provider(mercuryo)),
            quotes = quotes(items = listOf(quote(transak), quote(mercuryo))),
        )

        assertEquals(listOf(mercuryo), limits.orderedMerchants)
    }

    @Test
    fun `an amount nobody takes picks the nearest minimum above it`() {
        val limits = limits(
            providers = listOf(provider(mercuryo, min = "50"), provider(transak, min = "30"), provider(moonpay, max = "5")),
        )

        assertFalse(limits.hasServiceableMerchant(BigDecimal("10")))
        assertEquals(transak, limits.bestMerchant(BigDecimal("10")))
    }

    @Test
    fun `an amount above every maximum falls back to the first merchant`() {
        val limits = limits(providers = listOf(provider(mercuryo, max = "100"), provider(transak, max = "200")))

        assertEquals(mercuryo, limits.bestMerchant(BigDecimal("500")))
    }

    @Test
    fun `a violation names the merchant's limit`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "20", max = "100")))

        assertEquals(RampLimitViolation.BelowMin(BigDecimal("20")), limits.violation(mercuryo, BigDecimal("10")))
        assertEquals(RampLimitViolation.AboveMax(BigDecimal("100")), limits.violation(mercuryo, BigDecimal("101")))
        assertNull(limits.violation(mercuryo, BigDecimal("50")))
        assertNull(limits.violation(mercuryo, BigDecimal.ZERO))
    }

    @Test
    fun `without a merchant the widest limits of the method apply`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "20", max = "100"), provider(transak, min = "10", max = "300")))

        assertEquals(RampLimitViolation.BelowMin(BigDecimal("10")), limits.violation(null, BigDecimal("5")))
        assertNull(limits.violation(null, BigDecimal("250")))
        assertEquals(RampLimitViolation.AboveMax(BigDecimal("300")), limits.violation(null, BigDecimal("301")))
    }

    @Test
    fun `a merchant without limits leaves the others' bounds for the widest range, as on iOS`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "25", max = "300"), provider(transak)))

        assertEquals(RampLimitViolation.BelowMin(BigDecimal("25")), limits.violation(null, BigDecimal("5")))
        assertNull(limits.ceiling)
    }

    @Test
    fun `the ceiling is the highest maximum when every merchant sets one`() {
        val limits = limits(providers = listOf(provider(mercuryo, min = "25", max = "300"), provider(transak, max = "1000")))

        assertEquals(BigDecimal("1000"), limits.ceiling)
    }

    private fun limits(providers: List<RampProvider>, quotes: RampQuotes? = null): RampLimits {
        return RampLimits(providers, quotes, fiatScale = 2)
    }

    private fun provider(merchant: ExchangeMerchantSlug, min: String? = null, max: String? = null): RampProvider {
        return RampProvider(merchant = merchant, fiat = "USD", limits = ExchangeLimits(min = min, max = max))
    }

    private fun quotes(items: List<RampQuote> = emptyList(), suggested: List<RampQuote> = emptyList()): RampQuotes {
        return RampQuotes(items = items, suggested = suggested, unavailableReason = null)
    }

    private fun quote(merchant: ExchangeMerchantSlug, min: String? = null, max: String? = null): RampQuote {
        return RampQuote(
            merchant = merchant,
            paymentMethod = ExchangePaymentMethodType.card,
            amountIn = "100",
            amountOut = "99",
            rate = "0.99",
            fees = RampFees(total = "1"),
            merchantTransactionId = "tx-$merchant",
            minAmount = min,
            maxAmount = max,
        )
    }
}

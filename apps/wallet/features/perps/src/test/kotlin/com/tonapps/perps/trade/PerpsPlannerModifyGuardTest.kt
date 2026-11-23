package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsRestingOrder
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class PerpsPlannerModifyGuardTest {

    @Test
    fun `the size shown on the row passes`() {
        checkModifyAgainstSeen(resting(remaining = 1_200L), modify(seen = "0.0120"), SCALE)
    }

    @Test
    fun `a partially filled order passes`() {
        checkModifyAgainstSeen(resting(remaining = 800L), modify(seen = "0.0120"), SCALE)
    }

    @Test
    fun `an order one lot bigger than shown is refused`() {
        assertStale { checkModifyAgainstSeen(resting(remaining = 1_201L), modify(seen = "0.0120"), SCALE) }
    }

    @Test
    fun `the confirmed old price passes`() {
        checkModifyAgainstSeen(
            resting(remaining = 1_200L, limitPrice = 180_000L),
            modify(seen = "0.0120", expectedLimitPrice = "1800.00"),
            SCALE,
        )
    }

    @Test
    fun `a moved old price is refused`() {
        assertStale {
            checkModifyAgainstSeen(
                resting(remaining = 1_200L, limitPrice = 180_100L),
                modify(seen = "0.0120", expectedLimitPrice = "1800.00"),
                SCALE,
            )
        }
    }

    @Test
    fun `a seen size finer than a lot rounds down`() {
        checkModifyAgainstSeen(resting(remaining = 1_200L), modify(seen = "0.012005"), SCALE)
        assertStale { checkModifyAgainstSeen(resting(remaining = 1_201L), modify(seen = "0.012005"), SCALE) }
    }

    private fun assertStale(block: () -> Unit) {
        val error = assertThrows(PerpsTradeException::class.java) { block() }

        assertEquals(PerpsTradeError.StaleInput, error.kind)
    }

    private fun modify(seen: String, expectedLimitPrice: String? = null) = PerpsTradeRequest.OrderModify(
        walletId = WALLET_ID,
        marketIndex = MARKET_INDEX,
        orderIndex = ORDER_INDEX,
        price = BigDecimal("2118.20"),
        maxBaseAmount = BigDecimal(seen),
        expectedLimitPrice = expectedLimitPrice?.let(::BigDecimal),
    )

    private fun resting(remaining: Long, limitPrice: Long = 180_000L) = PerpsRestingOrder(
        orderIndex = ORDER_INDEX,
        side = PerpsSide.Long,
        category = PerpsOrderCategory.Open,
        orderType = PerpsRestingOrderType.Limit,
        status = PerpsRestingOrderStatus.Open,
        reduceOnly = false,
        positionTied = false,
        remainingBaseAmount = remaining,
        limitPrice = limitPrice,
        triggerPrice = null,
        expiryUnixMs = null,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 1
        const val ORDER_INDEX = 42L
        val SCALE = PerpsScale(priceDecimals = 2, baseDecimals = 5, quoteDecimals = 6)
    }
}

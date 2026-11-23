package com.tonapps.deposit.multicoin

import com.tonapps.deposit.screens.ramp.RampType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyCryptoEntryTest {

    @Test
    fun `selling to a card never opens the buying screen`() {
        assertFalse(buyCryptoEnabled(RampType.RampOff, flagEnabled = true))
        assertTrue(buyCryptoEnabled(RampType.RampOn, flagEnabled = true))
        assertFalse(buyCryptoEnabled(RampType.RampOn, flagEnabled = false))
    }

    @Test
    fun `a flow that starts on an asset opens the buying screen on it`() {
        val start = startRoute(DepositMulticoinRoutes.Buy(assetId = "ton/mainnet/coin", preferredCurrency = "EUR"), buyCrypto = true)

        assertEquals(DepositMulticoinRoutes.BuyCrypto(assetId = "ton/mainnet/coin", preferredCurrency = "EUR", open = "x"), (start as DepositMulticoinRoutes.BuyCrypto).copy(open = "x"))
    }

    @Test
    fun `without the buying screen every start stays as it was`() {
        val buy = DepositMulticoinRoutes.Buy(assetId = "ton/mainnet/coin")

        assertEquals(buy, startRoute(buy, buyCrypto = false))
        assertEquals(DepositMulticoinRoutes.Ramp, startRoute(null, buyCrypto = false))
    }

    @Test
    fun `a flow without a start skips Add Funds for the buying screen`() {
        val start = startRoute(null, buyCrypto = true) as DepositMulticoinRoutes.BuyCrypto

        assertEquals(DepositMulticoinRoutes.BuyCrypto(open = "x"), start.copy(open = "x"))
    }

    @Test
    fun `receiving keeps its own start with the buying screen on`() {
        assertEquals(DepositMulticoinRoutes.Receive, startRoute(DepositMulticoinRoutes.Receive, buyCrypto = true))
    }
}

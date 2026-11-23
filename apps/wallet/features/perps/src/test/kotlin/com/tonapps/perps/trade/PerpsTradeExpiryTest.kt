package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.wallet.data.multichain.perps.PerpsStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsStepRecord
import com.tonapps.wallet.data.multichain.perps.PerpsStepStatus
import com.tonapps.wallet.testing.FakeTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsTradeExpiryTest {

    private val serverTime = FakeTimeProvider()

    @Test
    fun aTradeExpiresItsLifetimeAfterTheServersNow() {
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val before = serverTime.currentTimeMillis()

        val expiry = transactionExpiryUnixMs(serverTime)

        val after = serverTime.currentTimeMillis()
        val lifetime = LighterConstants.DefaultExpireTimeMillis
        assertTrue(expiry in before + lifetime..after + lifetime)
    }

    @Test
    fun aTradeExpiryStopsAtTheLargestTimestamp() {
        serverTime.offsetMs = LighterConstants.MaxTimestamp

        assertEquals(LighterConstants.MaxTimestamp, transactionExpiryUnixMs(serverTime))
    }

    @Test
    fun aStepAliveOnTheServersClockBlocksOrdersPastItsExpiryOnADeviceClockAhead() {
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val step = step(txExpiry = serverTime.currentTimeMillis() + ALIVE_FOR_MS)

        assertTrue(step.blocksPositionOrders(serverTime))
    }

    @Test
    fun aStepLapsedOnTheServersClockStopsBlockingOrdersOnADeviceClockBehind() {
        serverTime.offsetMs = DEVICE_CLOCK_SKEW_MS
        val step = step(txExpiry = System.currentTimeMillis() + ALIVE_FOR_MS)

        assertFalse(step.blocksPositionOrders(serverTime))
    }

    @Test
    fun aStepAliveOnTheServersClockCanStillLandOnADeviceClockAhead() {
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val step = step(txExpiry = serverTime.currentTimeMillis() + ALIVE_FOR_MS)

        assertFalse(step.unresolvable(serverTime))
    }

    @Test
    fun aStepLapsedOnTheServersClockCannotLandOnADeviceClockBehind() {
        serverTime.offsetMs = DEVICE_CLOCK_SKEW_MS
        val step = step(txExpiry = System.currentTimeMillis() + ALIVE_FOR_MS)

        assertTrue(step.unresolvable(serverTime))
    }

    private fun step(txExpiry: Long) = PerpsStepRecord(
        operationId = "operation",
        stepId = "open",
        kind = PerpsStepKind.OPEN,
        requiresStepId = null,
        attemptId = "attempt",
        apiKeyIndex = 3,
        nonce = 1,
        accountIndex = 7,
        txType = 14,
        txHash = "0xhash",
        txInfo = "{}",
        clientOrderIndex = 42,
        planId = null,
        txExpiry = txExpiry,
        orderRefs = emptyList(),
        submittedAt = System.currentTimeMillis(),
        status = PerpsStepStatus.SENT,
        error = null,
        updatedAt = 0,
    )

    private companion object {
        const val DEVICE_CLOCK_SKEW_MS = 60L * 60L * 1_000L
        const val ALIVE_FOR_MS = 60_000L
    }
}

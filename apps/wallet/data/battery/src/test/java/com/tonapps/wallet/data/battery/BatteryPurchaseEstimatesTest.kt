package com.tonapps.wallet.data.battery

import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryPurchaseEstimatesTest {

    private val asked = mutableListOf<BigInteger>()
    private val answered = mutableListOf<BigInteger>()

    @Test
    fun anAmountIsEstimatedOnceItStands() = runTest(StandardTestDispatcher()) {
        val amounts = MutableStateFlow<BigInteger?>(null)
        val seen = collect(amounts, responseMs = 0)

        amounts.value = BigInteger("10000000")
        runCurrent()
        assertEquals(BatteryPurchaseEstimate.Loading(BigInteger("10000000")), seen.last())
        assertTrue(asked.isEmpty())

        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(listOf(BigInteger("10000000")), asked)
        val ready = seen.last() as BatteryPurchaseEstimate.Ready
        assertEquals(BigInteger("10000000"), ready.amount)
        assertEquals(10L, ready.quote.charges)
    }

    @Test
    fun typingWithinTheDebounceAsksOnlyForTheLastAmount() = runTest(StandardTestDispatcher()) {
        val amounts = MutableStateFlow<BigInteger?>(null)
        val seen = collect(amounts, responseMs = 0)

        listOf("1", "10", "100").forEach {
            amounts.value = BigInteger(it)
            runCurrent()
            advanceTimeBy(DEBOUNCE_MS / 2)
        }
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(BigInteger("100")), asked)
        assertEquals(BigInteger("100"), (seen.last() as BatteryPurchaseEstimate.Ready).amount)
    }

    @Test
    fun aNewAmountCancelsTheRequestInFlight() = runTest(StandardTestDispatcher()) {
        val amounts = MutableStateFlow<BigInteger?>(null)
        val seen = collect(amounts, responseMs = 1_000)

        amounts.value = BigInteger("1")
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS + 100)
        runCurrent()
        assertEquals(listOf(BigInteger("1")), asked)

        // The answer for 1 is still on its way when 2 is typed: it is never shown.
        amounts.value = BigInteger("2")
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS + 1_100)
        runCurrent()

        assertEquals(listOf(BigInteger("2")), answered)
        assertTrue(seen.none { it is BatteryPurchaseEstimate.Ready && it.amount == BigInteger("1") })
        assertEquals(BigInteger("2"), (seen.last() as BatteryPurchaseEstimate.Ready).amount)
    }

    @Test
    fun clearingTheAmountShowsNothing() = runTest(StandardTestDispatcher()) {
        val amounts = MutableStateFlow<BigInteger?>(BigInteger("5"))
        val seen = collect(amounts, responseMs = 0)
        runCurrent()

        amounts.value = BigInteger.ZERO
        runCurrent()
        advanceTimeBy(DEBOUNCE_MS * 2)
        runCurrent()

        assertEquals(BatteryPurchaseEstimate.None, seen.last())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aFailedEstimateIsReportedForItsAmount() = runTest(StandardTestDispatcher()) {
        val amounts = MutableStateFlow<BigInteger?>(BigInteger("7"))
        val seen = mutableListOf<BatteryPurchaseEstimate>()
        amounts.purchaseEstimates(DEBOUNCE_MS, { it }) { throw IllegalStateException("below minimum") }
            .onEach { seen += it }
            .launchIn(backgroundScope)

        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        val failed = seen.last() as BatteryPurchaseEstimate.Failed
        assertEquals(BigInteger("7"), failed.amount)
        assertEquals("below minimum", failed.error.message)
    }

    private fun TestScope.collect(amounts: MutableStateFlow<BigInteger?>, responseMs: Long): List<BatteryPurchaseEstimate> {
        val seen = mutableListOf<BatteryPurchaseEstimate>()
        amounts.purchaseEstimates(DEBOUNCE_MS, { it }) { amount ->
            asked += amount
            delay(responseMs)
            answered += amount
            quote(amount)
        }
            .onEach { seen += it }
            .launchIn(backgroundScope)
        return seen
    }

    private fun quote(amount: BigInteger) = BatteryPurchaseQuote(
        chainId = "base/mainnet",
        assetId = "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
        senderAddress = "0xsender",
        amount = amount,
        grossCharges = 12,
        charges = 10,
        fundsReceiver = "0xreceiver",
        promoBonusPercent = null,
    )

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}

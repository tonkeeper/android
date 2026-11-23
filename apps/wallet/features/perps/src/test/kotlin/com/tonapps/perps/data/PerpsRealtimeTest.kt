package com.tonapps.perps.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsRealtimeTest {

    @Test
    fun `the perps channel is subscribed only while a screen asks for it and left after a pause`() = runTest {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        runCurrent()
        assertTrue(env.subscribedChannels.isEmpty())

        env.connectedWalletId.value = WALLET
        runCurrent()
        assertTrue(env.subscribedChannels.isEmpty())

        val demand = env.realtime.demand().apply { set(true) }
        runCurrent()
        assertEquals(listOf(env.channel(WALLET)), env.subscribedChannels)

        demand.set(false)
        advanceTimeBy(DEMAND_LINGER_MS - 1)
        runCurrent()
        assertTrue(env.unsubscribedChannels.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(env.channel(WALLET)), env.unsubscribedChannels)
    }

    @Test
    fun `a demand that comes back within the pause keeps the subscription`() = runTest {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        val demand = env.connect(WALLET)
        runCurrent()

        demand.set(false)
        advanceTimeBy(DEMAND_LINGER_MS / 2)
        demand.set(true)
        advanceTimeBy(DEMAND_LINGER_MS * 2)
        runCurrent()

        assertEquals(listOf(env.channel(WALLET)), env.subscribedChannels)
        assertTrue(env.unsubscribedChannels.isEmpty())
    }

    @Test
    fun `switching wallets leaves the previous channel`() = runTest {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        env.connect(WALLET)
        runCurrent()
        env.confirmSubscription(WALLET)
        runCurrent()
        assertTrue(env.realtime.isSubscribed(WALLET))

        env.connectedWalletId.value = OTHER_WALLET
        runCurrent()

        assertEquals(listOf(env.channel(WALLET)), env.unsubscribedChannels)
        assertEquals(listOf(env.channel(WALLET), env.channel(OTHER_WALLET)), env.subscribedChannels)
        assertNull(env.realtime.subscribedWalletId.value)
    }

    @Test
    fun `a closed realtime connection leaves the channel`() = runTest {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        env.connect(WALLET)
        runCurrent()

        env.connectedWalletId.value = null
        runCurrent()

        assertEquals(listOf(env.channel(WALLET)), env.unsubscribedChannels)
    }

    @Test
    fun `events of the subscribed wallet are read from the envelope`() = runTest {
        val env = subscribedEnv()
        val events = collectEvents(env)

        env.publishMarket(WALLET, marketIndex = 1, seq = 10)
        env.publish(WALLET, "perps.account", seq = 11)
        env.publish(WALLET, "perps.deposit", seq = 12)
        env.publish(WALLET, "perps.withdrawal", seq = 13)
        runCurrent()

        assertEquals(
            listOf(
                PerpsRealtimeEvent.Market(WALLET, 1),
                PerpsRealtimeEvent.Account(WALLET),
                PerpsRealtimeEvent.Deposit(WALLET),
                PerpsRealtimeEvent.Withdrawal(WALLET),
            ),
            events,
        )
    }

    @Test
    fun `events that are stale, foreign, unknown or of another version are dropped`() = runTest {
        val env = subscribedEnv()
        val events = collectEvents(env)

        env.publishMarket(WALLET, marketIndex = 1, seq = 20)
        env.publishMarket(WALLET, marketIndex = 1, seq = 19)
        env.publishMarket(WALLET, marketIndex = 2, seq = 19)
        env.publish(WALLET, "perps.account", seq = 21, envelopeWalletId = OTHER_WALLET)
        env.publish(WALLET, "perps.account", seq = 22, version = 2)
        env.publish(WALLET, "perps.funding", seq = 23)
        env.publish(WALLET, "perps.market", seq = 24)
        env.publish(OTHER_WALLET, "perps.account", seq = 25)
        env.signals.tryEmit(
            com.tonapps.wallet.api.realtime.RealtimeSignal.Publication(env.channel(WALLET), "{".encodeToByteArray())
        )
        runCurrent()

        assertEquals(
            listOf(PerpsRealtimeEvent.Market(WALLET, 1), PerpsRealtimeEvent.Market(WALLET, 2)),
            events,
        )
    }

    @Test
    fun `without a subscription the wakeups tick at the slow period`() = runTest {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        val wakes = collectWakes(env) { true }

        advanceTimeBy(PERPS_SLOW_POLL_MS * 3)
        runCurrent()

        assertEquals(List(3) { PerpsWake.TICK }, wakes)
    }

    @Test
    fun `a subscription wakes on the events it was asked for and nothing else`() = runTest {
        val env = subscribedEnv()
        val wakes = collectWakes(env) { it is PerpsRealtimeEvent.Market && it.marketIndex == 1 }

        env.publishMarket(WALLET, marketIndex = 2, seq = 1)
        env.publish(WALLET, "perps.account", seq = 2)
        runCurrent()
        assertTrue(wakes.isEmpty())

        env.publishMarket(WALLET, marketIndex = 1, seq = 3)
        runCurrent()
        advanceTimeBy(PERPS_SLOW_POLL_MS * 3)
        runCurrent()

        assertEquals(listOf(PerpsWake.EVENT), wakes)
    }

    @Test
    fun `a dropped subscription falls back to the slow period and catches up once it is back`() = runTest {
        val env = subscribedEnv()
        val wakes = collectWakes(env) { true }

        env.dropSubscription(WALLET)
        runCurrent()
        assertFalse(env.realtime.isSubscribed(WALLET))
        advanceTimeBy(PERPS_SLOW_POLL_MS)
        runCurrent()

        env.confirmSubscription(WALLET, resubscribed = true)
        runCurrent()

        assertEquals(listOf(PerpsWake.TICK, PerpsWake.RESUBSCRIBED), wakes)
    }

    @Test
    fun `a silent subscription wakes after the timeout when one is asked for`() = runTest {
        val env = subscribedEnv()
        val wakes = mutableListOf<PerpsWake>()
        backgroundScope.launch {
            env.realtime.wakeups(WALLET, unsubscribedPeriodMs = 1_000, subscribedTimeoutMs = TIMEOUT_MS) { true }
                .toList(wakes)
        }
        runCurrent()

        advanceTimeBy(TIMEOUT_MS - 1)
        runCurrent()
        env.publish(WALLET, "perps.account", seq = 1)
        runCurrent()
        advanceTimeBy(TIMEOUT_MS)
        runCurrent()

        assertEquals(listOf(PerpsWake.EVENT, PerpsWake.SILENCE), wakes)
    }

    @Test
    fun `an attempt is repeated on each wakeup until it settles`() = runTest {
        val env = subscribedEnv()
        var attempts = 0
        var result: String? = null
        backgroundScope.launch {
            result = env.realtime.pollUntil(WALLET, unsubscribedPeriodMs = 1_000, subscribedTimeoutMs = null, { true }) {
                attempts++
                "done".takeIf { attempts == 3 }
            }
        }
        runCurrent()
        assertEquals(1, attempts)

        env.publish(WALLET, "perps.account", seq = 1)
        runCurrent()
        env.publish(WALLET, "perps.account", seq = 2)
        runCurrent()

        assertEquals(3, attempts)
        assertEquals("done", result)
    }

    private fun TestScope.subscribedEnv(): PerpsRealtimeTestEnv {
        val env = PerpsRealtimeTestEnv(backgroundScope)
        env.connect(WALLET)
        runCurrent()
        env.confirmSubscription(WALLET)
        runCurrent()
        assertTrue(env.realtime.isSubscribed(WALLET))
        return env
    }

    private fun TestScope.collectEvents(env: PerpsRealtimeTestEnv): List<PerpsRealtimeEvent> {
        val events = mutableListOf<PerpsRealtimeEvent>()
        backgroundScope.launch { env.realtime.events.toList(events) }
        runCurrent()
        return events
    }

    private fun TestScope.collectWakes(
        env: PerpsRealtimeTestEnv,
        wakeOn: (PerpsRealtimeEvent) -> Boolean,
    ): List<PerpsWake> {
        val wakes = mutableListOf<PerpsWake>()
        backgroundScope.launch {
            env.realtime.wakeups(WALLET, unsubscribedPeriodMs = PERPS_SLOW_POLL_MS, wakeOn = wakeOn).toList(wakes)
        }
        runCurrent()
        return wakes
    }

    private companion object {
        const val WALLET = "aiayhryh3phrjbrnwbttunftczzpt6ahm6ra"
        const val OTHER_WALLET = "aiotherwallet0000000000000000000000a"
        const val DEMAND_LINGER_MS = 5_000L
        const val TIMEOUT_MS = 5_000L
    }
}

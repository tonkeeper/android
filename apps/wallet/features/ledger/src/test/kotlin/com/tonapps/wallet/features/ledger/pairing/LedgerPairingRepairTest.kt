package com.tonapps.wallet.features.ledger.pairing

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.IssueHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.walletimport.WalletImportPairingSessions
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.test.TestDispatchersRule
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LedgerPairingRepairTest {

    @get:Rule
    val dispatchers = TestDispatchersRule()

    private val scheduler get() = dispatchers.scheduler

    private val environment = mockk<LedgerBleEnvironment> { every { check() } returns LedgerBleReadiness.Ready }
    private val controller = mockk<LedgerController>(relaxed = true) {
        every { state } returns MutableStateFlow<LedgerOperationState>(LedgerOperationState.Idle)
    }
    private val wallets = mockk<LedgerWalletInteractor>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }
    private val issues = CopyOnWriteArrayList<Throwable>()

    @Before
    fun setUp() {
        val silent = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) = Unit
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns mockk { every { events } returns DefaultEvents(silent) }
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
        mockkObject(IssueHelper)
        every { IssueHelper.recordException(any(), any<Map<String, String>>()) } answers { issues += firstArg<Throwable>() }
    }

    @After
    fun tearDown() {
        store.clear()
        unmockkAll()
    }

    @Test
    fun `an error saving the new device returns to the device list instead of staying on connecting`() {
        val error = IllegalStateException("database is locked")
        coEvery { wallets.rebind(WALLET_ID, any()) } throws error
        val feature = LedgerPairingFeature(
            LedgerPairingMode.Repair(WALLET_ID),
            environment,
            mockk(relaxed = true),
            mockk(relaxed = true),
            controller,
            mockk(),
            wallets,
            mockk(),
            WalletImportPairingSessions(),
        )
        store.put("pairing", feature)
        val events = CopyOnWriteArrayList<LedgerPairingEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.advanceUntilIdle()

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        assertEquals(LedgerPairingStep.Connect, feature.state.value.step)
        assertNull(feature.state.value.device)
        assertTrue(events.none { it == LedgerPairingEvent.Repaired })
        assertEquals(listOf<Throwable>(error), issues)
    }

    private companion object {
        const val WALLET_ID = "ledger-wallet"
        val DEVICE = LedgerBleDevice("C1:2B:3C:4D:5E:6F", "Stax", LedgerModel.Stax, rssi = 0, lastSeenAt = 0)
    }
}

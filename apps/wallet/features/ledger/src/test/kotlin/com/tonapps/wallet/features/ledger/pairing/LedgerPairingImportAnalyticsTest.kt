package com.tonapps.wallet.features.ledger.pairing

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.bus.walletimport.WalletImportPairingSessions
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOpenLink
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.test.TestDispatchersRule
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveredAccount
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractor
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryResult
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.tonapps.ledgerkit.transport.ble.LedgerBleScanner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LedgerPairingImportAnalyticsTest {

    @get:Rule
    val dispatchers = TestDispatchersRule()

    private val scheduler get() = dispatchers.scheduler

    private val link = MutableStateFlow<LedgerOpenLink?>(null)
    private val environment = mockk<LedgerBleEnvironment> { every { check() } returns LedgerBleReadiness.Ready }
    private val controller = mockk<LedgerController>(relaxed = true) {
        every { state } returns MutableStateFlow<LedgerOperationState>(LedgerOperationState.Idle)
        every { link } returns this@LedgerPairingImportAnalyticsTest.link
    }
    private val discovery = mockk<LedgerDiscoveryInteractor>()
    private val accounts = mockk<McAccountRepository> {
        coEvery { findWalletIdsByAddress(any(), any()) } returns emptyList()
    }
    private val store = ViewModelStore()
    private val scanner = mockk<LedgerBleScanner> { every { scan() } returns flowOf(listOf(DEVICE), listOf(DEVICE)) }
    private val tracked = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()

    @Before
    fun setUp() {
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns mockk { every { events } returns DefaultEvents(recording) }
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        store.clear()
        unmockkAll()
    }

    private fun feature(pairingSessions: WalletImportPairingSessions = WalletImportPairingSessions()): LedgerPairingFeature {
        val feature = LedgerPairingFeature(
            LedgerPairingMode.New(WalletFlowFrom.Main),
            environment,
            scanner,
            mockk(relaxed = true),
            controller,
            discovery,
            mockk(relaxed = true),
            accounts,
            pairingSessions,
        )
        store.put("pairing", feature)
        scheduler.advanceUntilIdle()
        return feature
    }

    private fun discovered() = LedgerDiscoveryResult(
        accounts = listOf(
            LedgerDiscoveredAccount(
                index = 0,
                accounts = listOf(HardwareAccountDraft(Chain.Ton.Mainnet, Address.Type.TonV4R2, TON_ADDRESS, TON_KEY)),
                bitcoinMasterFingerprint = null,
                bitcoinAccountXpub = null,
            ),
        ),
        apps = mapOf(
            LedgerChainApp.Ton to LedgerAppDiscoveryState.Done,
            LedgerChainApp.Ethereum to LedgerAppDiscoveryState.Done,
            LedgerChainApp.Tron to LedgerAppDiscoveryState.Skipped(LedgerFailure.AppNotInstalled(LedgerAppId.Tron)),
        ),
    )

    private fun event(name: String): Map<String, Any> = tracked.single { it.first == name }.second

    @Test
    fun `pairing a ledger over bluetooth reports the funnel steps with the model and the apps it holds`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered()
        val feature = feature()
        link.value = LedgerOpenLink(DEVICE.address, LedgerAppInfo("TON", "2.9.1"), LedgerLinkKind.Bluetooth)

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                "wallet_import_ledger_bluetooth_connected",
                "wallet_import_ledger_app_opened",
                "ledger_discovery_error",
                "wallet_import_ledger_accounts_view",
            ),
            tracked.map { it.first },
        )
        val funnel = mapOf("wallet_mode" to "multi", "wallet_source" to "ledger", "from" to "main", "ledger_model" to "stax")
        assertEquals(funnel, event("wallet_import_ledger_bluetooth_connected"))
        assertEquals(funnel + ("ledger_apps" to "ethereum,ton"), event("wallet_import_ledger_accounts_view"))
        assertEquals(
            mapOf("from" to "import", "ledger_app" to "tron", "failure_type" to "app_not_installed", "ledger_model" to "stax"),
            event("ledger_discovery_error"),
        )
    }

    @Test
    fun `a ledger on the cable reports a usb connection`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered()
        val feature = feature()
        link.value = LedgerOpenLink(DEVICE.address, null, LedgerLinkKind.Usb)

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        assertEquals("stax", event("wallet_import_ledger_usb_connected")["ledger_model"])
    }

    @Test
    fun `a failed read reports an import error with a stable ledger failure type`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } throws
            LedgerFailureException(LedgerFailure.DeviceLocked)
        val feature = feature()

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        val error = event("wallet_import_error")
        assertEquals("device_locked", error["error_type"])
        assertEquals("multi", error["wallet_mode"])
        assertEquals("stax", error["ledger_model"])
    }

    @Test
    fun `an import session reports the search, each found device once and the furthest step on leaving`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered()
        val pairingSessions = WalletImportPairingSessions()
        pairingSessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)
        val feature = feature(pairingSessions)
        feature.onPermissionsResult(granted = true)
        scheduler.advanceUntilIdle()
        link.value = LedgerOpenLink(DEVICE.address, LedgerAppInfo("TON", "2.9.1"), LedgerLinkKind.Bluetooth)

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()
        store.clear()

        assertEquals(
            listOf(
                "wallet_import_started",
                "wallet_import_pairing_action",
                "wallet_import_ledger_device_found",
                "wallet_import_ledger_bluetooth_connected",
                "wallet_import_ledger_app_opened",
                "ledger_discovery_error",
                "wallet_import_ledger_accounts_view",
                "wallet_import_pairing_exit",
            ),
            tracked.map { it.first },
        )
        val funnel = mapOf("wallet_mode" to "multi", "wallet_source" to "ledger", "from" to "main")
        assertEquals(funnel + ("pairing_entry" to "menu"), event("wallet_import_started"))
        assertEquals(funnel + ("pairing_action" to "device_search_started"), event("wallet_import_pairing_action"))
        assertEquals(funnel + ("ledger_transport" to "bluetooth") + ("ledger_model" to "stax"), event("wallet_import_ledger_device_found"))
        assertEquals(
            funnel + ("pairing_last_step" to "accounts_view") + ("pairing_exit_reason" to "back"),
            event("wallet_import_pairing_exit"),
        )
    }

    @Test
    fun `an import session handed to the import screen does not report leaving`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered()
        val pairingSessions = WalletImportPairingSessions()
        pairingSessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)
        val feature = feature(pairingSessions)
        link.value = LedgerOpenLink(DEVICE.address, LedgerAppInfo("TON", "2.9.1"), LedgerLinkKind.Bluetooth)
        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        feature.importSelected()
        scheduler.advanceUntilIdle()
        store.clear()

        assertEquals(emptyList<String>(), tracked.map { it.first }.filter { it == "wallet_import_pairing_exit" })
    }

    @Test
    fun `a failed read before leaving is the reason of the exit`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } throws
            LedgerFailureException(LedgerFailure.DeviceLocked)
        val pairingSessions = WalletImportPairingSessions()
        pairingSessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)
        val feature = feature(pairingSessions)

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()
        store.clear()

        assertEquals("error", event("wallet_import_pairing_exit")["pairing_exit_reason"])
    }

    @Test
    fun `a read the user cancels is not reported as an error on leaving`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } throws
            LedgerFailureException(LedgerFailure.Cancelled)
        val pairingSessions = WalletImportPairingSessions()
        pairingSessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)
        val feature = feature(pairingSessions)

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()
        store.clear()

        assertEquals("back", event("wallet_import_pairing_exit")["pairing_exit_reason"])
    }

    private companion object {
        const val TON_KEY = "0b1f4a8e6c2d9e7f30a5b4c3d2e1f0112233445566778899aabbccddeeff0011"
        const val TON_ADDRESS = "UQLedgerAddress"
        val DEVICE = LedgerBleDevice("C1:2B:3C:4D:5E:6F", "Stax", LedgerModel.Stax, rssi = 0, lastSeenAt = 0)
    }
}

package com.tonapps.wallet.features.ledger.pairing

import androidx.lifecycle.ViewModelStore
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.walletimport.WalletImportPairingSessions
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.test.TestDispatchersRule
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerLegacyWallet
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveredAccount
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractor
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryResult
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LedgerPairingMigrationTest {

    @get:Rule
    val dispatchers = TestDispatchersRule()

    private val scheduler get() = dispatchers.scheduler

    private val environment = mockk<LedgerBleEnvironment> { every { check() } returns LedgerBleReadiness.Ready }
    private val controller = mockk<LedgerController>(relaxed = true) {
        every { state } returns MutableStateFlow<LedgerOperationState>(LedgerOperationState.Idle)
    }
    private val discovery = mockk<LedgerDiscoveryInteractor>()
    private val wallets = mockk<LedgerWalletInteractor>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }
    private val tracked = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()

    @Before
    fun setUp() {
        coEvery { wallets.legacyWallet(LEGACY_ID) } returns LedgerLegacyWallet(LEGACY_ID, "Old Ledger", ACCOUNT_INDEX, LEGACY_KEY, LEGACY_ADDRESS)
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns mockk { every { events } returns DefaultEvents(recording) }
    }

    @After
    fun tearDown() {
        store.clear()
        unmockkAll()
    }

    private fun feature(): Pair<LedgerPairingFeature, List<LedgerPairingEvent>> {
        val feature = LedgerPairingFeature(
            LedgerPairingMode.Migrate(LEGACY_ID, LedgerMigrationStartFrom.Tooltip),
            environment,
            mockk(relaxed = true),
            mockk(relaxed = true),
            controller,
            discovery,
            wallets,
            mockk(),
            WalletImportPairingSessions(),
        )
        store.put("pairing", feature)
        val events = CopyOnWriteArrayList<LedgerPairingEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.advanceUntilIdle()
        return feature to events
    }

    private fun discovered(
        tonKey: String,
        apps: Map<LedgerChainApp, LedgerAppDiscoveryState> = emptyMap(),
    ) = LedgerDiscoveryResult(
        accounts = listOf(
            LedgerDiscoveredAccount(
                index = ACCOUNT_INDEX,
                accounts = listOf(HardwareAccountDraft(Chain.Ton.Mainnet, Address.Type.TonV4R2, LEGACY_ADDRESS, tonKey)),
                bitcoinMasterFingerprint = null,
                bitcoinAccountXpub = null,
            ),
        ),
        apps = apps,
    )

    @Test
    fun `the migration reads only the legacy account and imports it without asking which accounts`() {
        coEvery { discovery.discover(any(), listOf(ACCOUNT_INDEX), any(), any(), any(), LEGACY_ADDRESS, any()) } returns discovered(LEGACY_KEY)
        val (feature, events) = feature()

        assertTrue(feature.state.value.isMigration)
        assertEquals(ACCOUNT_INDEX, feature.state.value.migration?.accountIndex)
        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        coVerify { wallets.prepareWalletSeeds(any(), match { it.single().descriptor.accountIndex == ACCOUNT_INDEX }) }
        assertTrue(events.any { it is LedgerPairingEvent.Import })
        assertEquals(LedgerPairingStep.Discovery, feature.state.value.step)
        assertEquals(listOf("ledger_migration_start" to mapOf("from" to "tooltip")), tracked.toList())
    }

    @Test
    fun `an app skipped while the migration reads the ledger is reported with the migration as its source`() {
        val skipped = mapOf(
            LedgerChainApp.Ton to LedgerAppDiscoveryState.Done,
            LedgerChainApp.Solana to LedgerAppDiscoveryState.Skipped(LedgerFailure.UserRefused(USER_REFUSED)),
        )
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered(LEGACY_KEY, skipped)
        val (feature, _) = feature()

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        assertEquals(
            mapOf("from" to "migration", "ledger_app" to "solana", "failure_type" to "user_refused", "ledger_model" to "stax"),
            tracked.single { it.first == "ledger_discovery_error" }.second,
        )
    }

    @Test
    fun `a ledger with another ton key is refused and nothing is imported`() {
        coEvery { discovery.discover(any(), any(), any(), any(), any(), any(), any()) } returns discovered(OTHER_KEY)
        val (feature, events) = feature()

        feature.selectDevice(DEVICE)
        scheduler.advanceUntilIdle()

        assertEquals(LedgerPairingStep.Connect, feature.state.value.step)
        assertEquals(LedgerFailure.AddressMismatch(LedgerAppId.Ton), feature.state.value.scanFailure)
        coVerify(exactly = 0) { wallets.prepareWalletSeeds(any(), any()) }
        assertTrue(events.none { it is LedgerPairingEvent.Import })
        val error = tracked.single { it.first == "ledger_migration_error" }.second
        assertEquals(mapOf("from" to "tooltip", "failure_type" to "address_mismatch", "ledger_model" to "stax"), error)
    }

    @Test
    fun `a wallet that is not a legacy ledger closes the migration`() {
        coEvery { wallets.legacyWallet(LEGACY_ID) } returns null
        val (_, events) = feature()

        assertTrue(events.contains(LedgerPairingEvent.Close))
    }

    private companion object {
        const val LEGACY_ID = "legacy-ledger"
        const val ACCOUNT_INDEX = 2
        const val LEGACY_KEY = "0b1f4a8e6c2d9e7f30a5b4c3d2e1f0112233445566778899aabbccddeeff0011"
        const val OTHER_KEY = "ffeeddccbbaa99887766554433221100f0e1d2c3b4a5968778695a4b3c2d1e0f"
        const val LEGACY_ADDRESS = "UQLegacyLedgerAddress"
        const val USER_REFUSED = 0x6985
        val DEVICE = LedgerBleDevice("C1:2B:3C:4D:5E:6F", "Stax", LedgerModel.Stax, rssi = 0, lastSeenAt = 0)
    }
}

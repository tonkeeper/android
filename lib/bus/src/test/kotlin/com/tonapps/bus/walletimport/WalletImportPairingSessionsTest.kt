package com.tonapps.bus.walletimport

import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerTransport
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingEntry
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingExitReason
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingLastStep
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WalletImportPairingSessionsTest {

    private val tracked = mutableListOf<Pair<String, Map<String, Any>>>()

    private val events = DefaultEvents(object : EventExecutor {
        override fun trackEvent(eventName: String, props: Map<String, Any>) {
            tracked += eventName to props
        }
    })

    private val sessions = WalletImportPairingSessions { events.walletFlow }

    private val signer = mapOf("wallet_mode" to "single", "wallet_source" to "signer", "from" to "main")

    private fun startSigner(
        entry: WalletFlowPairingEntry = WalletFlowPairingEntry.Menu,
        hasPairingScreen: Boolean = true,
    ) = sessions.start(
        walletMode = WalletFlowWalletMode.Single,
        walletSource = WalletFlowWalletSource.Signer,
        from = WalletFlowFrom.Main,
        entry = entry,
        hasPairingScreen = hasPairingScreen,
    )

    private fun names() = tracked.map { it.first }

    private fun event(name: String) = tracked.single { it.first == name }.second

    @Test
    fun `leaving before any action reports none with back`() {
        startSigner().exit()

        assertEquals(listOf("wallet_import_started", "wallet_import_pairing_exit"), names())
        assertEquals(signer + ("pairing_entry" to "menu"), event("wallet_import_started"))
        assertEquals(
            signer + ("pairing_last_step" to "none") + ("pairing_exit_reason" to "back"),
            event("wallet_import_pairing_exit"),
        )
    }

    @Test
    fun `only the first action of a session is reported`() {
        val session = startSigner()

        session.cameraStarted()
        session.cameraStarted()
        session.signerAppOpened()

        assertEquals(listOf("wallet_import_started", "wallet_import_pairing_action"), names())
        assertEquals(signer + ("pairing_action" to "camera_opened"), event("wallet_import_pairing_action"))
    }

    @Test
    fun `a device is reported once however often the search sees it`() {
        val session = sessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Onboarding)

        session.deviceFound("AA:BB", WalletFlowLedgerTransport.Bluetooth, WalletFlowLedgerModel.NanoX)
        session.deviceFound("aa:bb", WalletFlowLedgerTransport.Bluetooth, WalletFlowLedgerModel.NanoX)
        session.deviceFound("usb-stax", WalletFlowLedgerTransport.Usb, null)

        val found = tracked.filter { it.first == "wallet_import_ledger_device_found" }.map { it.second }
        assertEquals(
            listOf(
                mapOf("wallet_mode" to "multi", "wallet_source" to "ledger", "from" to "onboarding", "ledger_transport" to "bluetooth", "ledger_model" to "nano_x"),
                mapOf("wallet_mode" to "multi", "wallet_source" to "ledger", "from" to "onboarding", "ledger_transport" to "usb"),
            ),
            found,
        )
    }

    @Test
    fun `the furthest step never moves back`() {
        val session = sessions.start(WalletFlowWalletMode.Single, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)

        session.advance(WalletFlowPairingLastStep.AppOpened)
        session.connected()
        session.exit()

        assertEquals("app_opened", event("wallet_import_pairing_exit")["pairing_last_step"])
    }

    @Test
    fun `the latest unresolved blocker is the exit reason even after back`() {
        val session = startSigner()

        session.setBlocked(WalletImportPairingSession.Blocker.CameraPermissionDenied)
        session.exit(WalletFlowPairingExitReason.Back)

        assertEquals("camera_permission_denied", event("wallet_import_pairing_exit")["pairing_exit_reason"])
    }

    @Test
    fun `a camera that starts after a denial clears the blocker and counts as the action`() {
        val session = startSigner()

        session.setBlocked(WalletImportPairingSession.Blocker.CameraPermissionDenied)
        session.cameraStarted()
        session.exit()

        assertEquals("camera_opened", event("wallet_import_pairing_action")["pairing_action"])
        assertEquals(
            signer + ("pairing_last_step" to "action") + ("pairing_exit_reason" to "back"),
            event("wallet_import_pairing_exit"),
        )
    }

    @Test
    fun `a resolved blocker gives way to the one still open`() {
        val session = sessions.start(WalletFlowWalletMode.Multi, WalletFlowWalletSource.Ledger, WalletFlowFrom.Main)

        session.setBlocked(WalletImportPairingSession.Blocker.BluetoothPermissionDenied)
        session.setBlocked(WalletImportPairingSession.Blocker.BluetoothOff)
        session.setBlocked(WalletImportPairingSession.Blocker.BluetoothOff, isBlocked = false)
        session.exit()

        assertEquals("bluetooth_permission_denied", event("wallet_import_pairing_exit")["pairing_exit_reason"])
    }

    @Test
    fun `a successful import carries the entry and sends no exit`() {
        val session = startSigner()

        session.connected()
        session.success()
        session.exit()

        assertEquals(listOf("wallet_import_started", "wallet_import_success"), names())
        assertEquals(signer + ("pairing_entry" to "menu"), event("wallet_import_success"))
    }

    @Test
    fun `a session remembers its success and an exited one does not`() {
        val succeeded = startSigner()
        succeeded.success()
        val exited = sessions.start(WalletFlowWalletMode.Single, WalletFlowWalletSource.Keystone, WalletFlowFrom.Main)
        exited.exit()

        assertEquals(true, succeeded.hasSucceeded)
        assertEquals(false, exited.hasSucceeded)
    }

    @Test
    fun `a return from signer resumes the session started from the menu`() {
        val session = startSigner()

        session.signerAppOpened()
        val awaiting = sessions.active(WalletFlowWalletSource.Signer)
        assertSame(session, awaiting)
        assertEquals(true, awaiting?.isAwaitingSignerReturn)
        awaiting?.connected()
        awaiting?.success()

        assertEquals(listOf("wallet_import_started", "wallet_import_pairing_action", "wallet_import_success"), names())
        assertEquals("signer_app_opened", event("wallet_import_pairing_action")["pairing_action"])
        assertEquals("menu", event("wallet_import_success")["pairing_entry"])
    }

    @Test
    fun `a direct signer link sends start and success without pairing steps`() {
        val session = startSigner(entry = WalletFlowPairingEntry.AppQrScanner, hasPairingScreen = false)

        session.cameraStarted()
        session.connected()
        session.exit()

        assertEquals(listOf("wallet_import_started"), names())
        assertEquals("app_qr_scanner", event("wallet_import_started")["pairing_entry"])
    }

    @Test
    fun `a new session ends the one left unfinished`() {
        val signerSession = startSigner()
        signerSession.signerAppOpened()

        sessions.start(WalletFlowWalletMode.Single, WalletFlowWalletSource.Keystone, WalletFlowFrom.Main)

        assertEquals(
            signer + ("pairing_last_step" to "action") + ("pairing_exit_reason" to "other"),
            event("wallet_import_pairing_exit"),
        )
        assertNull(sessions.active(WalletFlowWalletSource.Signer))
    }
}

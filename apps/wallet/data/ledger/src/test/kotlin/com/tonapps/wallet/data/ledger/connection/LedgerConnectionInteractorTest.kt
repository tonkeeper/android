package com.tonapps.wallet.data.ledger.connection

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import com.tonapps.wallet.data.ledger.FakeLedgerTransport
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LedgerConnectionInteractorTest {

    private val radio = FakeRadio()

    private val refreshes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    private var pairedDevice = DEVICE

    private val accounts = mockk<McAccountRepository> {
        every { refreshTrigger } returns refreshes
        coEvery { getHardwareDescriptor(WALLET) } answers {
            HardwareWalletDescriptor(
                deviceId = pairedDevice,
                deviceName = "Stax 1A2B",
                accountIndex = 0,
                derivationPaths = emptyMap(),
            )
        }
    }

    private fun TestScope.controller(transport: LedgerTransport = FakeLedgerTransport("TON") { ApduResponse(ByteArray(0), 0x9000) }) =
        LedgerController(transport, backgroundScope, idleTimeout = 3.seconds, doneHold = 1.milliseconds)

    private fun TestScope.interactor(controller: LedgerController = controller()) =
        LedgerConnectionInteractorImpl(controller, accounts, LedgerActivityLog(controller, backgroundScope, LedgerActivityStore.None), radio)

    private fun TestScope.collect(interactor: LedgerConnectionInteractorImpl): List<LedgerConnectionStatus> {
        val seen = mutableListOf<LedgerConnectionStatus>()
        backgroundScope.launch { interactor.deviceStatus(DEVICE, LedgerScanPolicy.Detailed).collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun `bluetooth off is reported without scanning`() = runTest {
        radio.readiness = LedgerBleReadiness.BluetoothOff
        val seen = collect(interactor())
        assertEquals(listOf(LedgerConnectionStatus.BluetoothOff), seen)
        assertEquals(0, radio.scans)
    }

    @Test
    fun `missing permissions never start a scan`() = runTest {
        radio.readiness = LedgerBleReadiness.MissingPermissions(listOf("scan"))
        val seen = collect(interactor())
        advanceTimeBy(10.seconds)
        assertEquals(listOf(LedgerConnectionStatus.NoPermission), seen)
        assertEquals(0, radio.scans)
    }

    @Test
    fun `advertising device is nearby`() = runTest {
        radio.devices.value = listOf(device(DEVICE))
        val seen = collect(interactor())
        assertEquals(listOf(LedgerConnectionStatus.Searching, LedgerConnectionStatus.Nearby), seen)
    }

    @Test
    fun `other ledgers around do not count`() = runTest {
        radio.devices.value = listOf(device("CC:DD"))
        val seen = collect(interactor())
        advanceTimeBy(13.seconds)
        assertEquals(listOf(LedgerConnectionStatus.Searching, LedgerConnectionStatus.NotFound), seen)
    }

    @Test
    fun `a scan held back by the start limit waits for its slot instead of reporting not found`() = runTest {
        radio.slotWait = 20.seconds
        radio.devices.value = listOf(device(DEVICE))
        val seen = collect(interactor())
        advanceTimeBy(19.seconds)
        assertEquals(listOf(LedgerConnectionStatus.Searching), seen)
        assertEquals(0, radio.scans)

        advanceTimeBy(2.seconds)
        assertEquals(listOf(LedgerConnectionStatus.Searching, LedgerConnectionStatus.Nearby), seen)
        assertEquals(1, radio.scans)
    }

    @Test
    fun `device that shows up after a miss turns nearby on the next scan without flashing searching`() = runTest {
        val seen = collect(interactor())
        advanceTimeBy(13.seconds)
        radio.devices.value = listOf(device(DEVICE))
        advanceTimeBy(6.seconds)
        assertEquals(listOf(LedgerConnectionStatus.Searching, LedgerConnectionStatus.NotFound, LedgerConnectionStatus.Nearby), seen)
    }

    @Test
    fun `lab device is always nearby`() = runTest {
        radio.lab = true
        radio.readiness = LedgerBleReadiness.Unsupported
        assertEquals(listOf(LedgerConnectionStatus.Nearby), collect(interactor()))
    }

    @Test
    fun `running operation is busy, the parked session shows the open app until it closes`() = runTest {
        radio.devices.value = listOf(device(DEVICE))
        val controller = controller()
        val seen = collect(interactor(controller))
        val release = CompletableDeferred<Unit>()
        backgroundScope.launch {
            controller.run(LedgerRequest(DEVICE, "Stax", LedgerPurpose.SignTransaction)) {
                ensureApp(LedgerAppId.Ton)
                release.await()
            }
        }
        advanceTimeBy(1.seconds)
        runCurrent()
        assertTrue(seen.last() is LedgerConnectionStatus.Busy)
        assertEquals(LedgerPurpose.SignTransaction, (seen.last() as LedgerConnectionStatus.Busy).purpose)
        release.complete(Unit)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(LedgerConnectionStatus.Connected(LedgerAppId.Ton), seen.last())
        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(LedgerConnectionStatus.Nearby, seen.last())
        val afterLink = seen.dropWhile { it !is LedgerConnectionStatus.Connected }
        assertTrue(afterLink.none { it == LedgerConnectionStatus.Searching })
    }

    @Test
    fun `operation on another ledger does not touch this one`() = runTest {
        radio.devices.value = listOf(device(DEVICE))
        val controller = controller()
        val seen = collect(interactor(controller))
        val release = CompletableDeferred<Unit>()
        backgroundScope.launch {
            controller.run(LedgerRequest("CC:DD", "Nano", LedgerPurpose.SignTransaction)) { release.await() }
        }
        advanceTimeBy(2.seconds)
        assertEquals(listOf(LedgerConnectionStatus.Searching, LedgerConnectionStatus.Nearby), seen)
        release.complete(Unit)
    }

    @Test
    fun `check connection reads the open app and leaves the session parked`() = runTest {
        val controller = controller()
        val app = interactor(controller).checkConnection(WALLET)
        assertEquals("TON", app.name)
        assertEquals(DEVICE, controller.openLink()?.deviceId)
    }

    @Test
    fun `failed check is dismissed instead of waiting for a retry`() = runTest {
        val controller = controller(transport = object : LedgerTransport {
            override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink =
                throw LedgerFailureException(LedgerFailure.DeviceNotFound)
        })
        try {
            interactor(controller).checkConnection(WALLET)
            fail()
        } catch (e: LedgerFailureException) {
            assertEquals(LedgerFailure.DeviceNotFound, e.failure)
        }
        assertEquals(LedgerOperationState.Idle, controller.state.value)
    }

    @Test
    fun `activity follows the wallet to the device it is paired with again`() = runTest {
        val controller = controller()
        val interactor = interactor(controller)
        val seen = mutableListOf<List<LedgerActivity>>()
        backgroundScope.launch { interactor.activity(WALLET).collect { seen += it } }
        controller.run(LedgerRequest(OTHER_DEVICE, "Nano", LedgerPurpose.VerifyAddress)) { session.currentApp() }
        runCurrent()
        assertTrue(seen.last().isEmpty())

        pairedDevice = OTHER_DEVICE
        refreshes.emit(Unit)
        runCurrent()

        assertEquals(listOf(LedgerPurpose.VerifyAddress), seen.last().map { it.purpose })
    }

    @Test
    fun `activity is filtered to the wallet's device`() = runTest {
        val controller = controller()
        val interactor = interactor(controller)
        val seen = mutableListOf<List<LedgerActivity>>()
        backgroundScope.launch { interactor.activity(WALLET).collect { seen += it } }
        controller.run(LedgerRequest("CC:DD", "Nano", LedgerPurpose.VerifyAddress)) { session.currentApp() }
        controller.run(LedgerRequest(DEVICE, "Stax", LedgerPurpose.SignTransaction)) { session.currentApp() }
        runCurrent()
        assertEquals(listOf(LedgerPurpose.SignTransaction), seen.last().map { it.purpose })
        assertEquals(LedgerActivityOutcome.Done, seen.last().single().outcome)
    }

    @Test
    fun `plugged in ledger is connected over usb without a bluetooth scan`() = runTest {
        radio.usb.value = true
        radio.readiness = LedgerBleReadiness.BluetoothOff
        val seen = collect(interactor())
        advanceTimeBy(20.seconds)
        assertEquals(listOf(LedgerConnectionStatus.UsbConnected), seen)
        assertEquals(0, radio.scans)
    }

    @Test
    fun `unplugging a bluetooth ledger goes back to looking for it nearby`() = runTest {
        radio.usb.value = true
        radio.devices.value = listOf(device(DEVICE))
        val seen = collect(interactor())
        radio.usb.value = false
        runCurrent()
        assertEquals(listOf(LedgerConnectionStatus.UsbConnected, LedgerConnectionStatus.Searching, LedgerConnectionStatus.Nearby), seen)
    }

    @Test
    fun `usb paired ledger without a cable is not connected and never scans`() = runTest {
        val seen = mutableListOf<LedgerConnectionStatus>()
        backgroundScope.launch { interactor().deviceStatus(USB_DEVICE, LedgerScanPolicy.Detailed).collect { seen += it } }
        advanceTimeBy(20.seconds)
        assertEquals(listOf(LedgerConnectionStatus.UsbDisconnected), seen)
        assertEquals(0, radio.scans)
        radio.usb.value = true
        runCurrent()
        assertEquals(LedgerConnectionStatus.UsbConnected, seen.last())
    }

    @Test
    fun `link kind follows the cable and the open session`() = runTest {
        val controller = controller()
        val interactor = interactor(controller)
        val kinds = mutableListOf<LedgerLinkKind>()
        backgroundScope.launch { interactor.linkKind(DEVICE).collect { kinds += it } }
        runCurrent()
        radio.usb.value = true
        runCurrent()
        assertEquals(listOf(LedgerLinkKind.Bluetooth, LedgerLinkKind.Usb), kinds)
        val usbKinds = mutableListOf<LedgerLinkKind>()
        backgroundScope.launch { interactor.linkKind(USB_DEVICE).collect { usbKinds += it } }
        radio.usb.value = false
        runCurrent()
        assertEquals(listOf(LedgerLinkKind.Usb), usbKinds)
    }

    private fun device(address: String) = LedgerBleDevice(address, "Stax", LedgerModel.Stax, rssi = -50, lastSeenAt = 0)

    private class FakeRadio : LedgerRadio {
        var readiness: LedgerBleReadiness = LedgerBleReadiness.Ready
        val devices = MutableStateFlow<List<LedgerBleDevice>>(emptyList())
        val usb = MutableStateFlow(false)
        var lab = false
        var scans = 0
        var slotWait = Duration.ZERO

        override fun readiness(): LedgerBleReadiness = readiness

        override suspend fun awaitScanSlot() = delay(slotWait)

        override fun scan(): Flow<List<LedgerBleDevice>> = devices.onStart { scans++ }

        override fun isAlwaysPresent(deviceId: String): Boolean = lab

        override fun usbAttached(): Flow<Boolean> = usb

        override fun routesToUsb(deviceId: String): Boolean = LedgerUsbIds.routesToUsb(deviceId, usb.value)
    }

    private companion object {
        const val WALLET = "wallet"
        const val DEVICE = "AA:BB"
        const val OTHER_DEVICE = "CC:DD"
        const val USB_DEVICE = "usb:NanoSPlus"
    }
}

package com.tonapps.ledgerkit.transport

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.session.FakeLedgerDevice
import com.tonapps.ledgerkit.session.LedgerSession
import com.tonapps.ledgerkit.session.LedgerSessionTimings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LedgerRoutingTransportTest {

    private class Unreachable(private val failure: LedgerFailure) : LedgerTransport {

        var connects = 0

        override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
            connects++
            throw LedgerFailureException(failure)
        }
    }

    private class StillConnecting : LedgerTransport {

        var started = false
        var cancelled = false

        override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
            started = true
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
    }

    private val clock = TestTimeSource()
    private val usb = FakeLedgerDevice(currentApp = "TON")
    private val bluetooth = FakeLedgerDevice(currentApp = "TON")
    private var attached = false

    private val router = LedgerRoutingTransport(usb, bluetooth, isUsbAttached = { attached }, timeSource = clock)

    @Test
    fun `a bluetooth wallet goes over bluetooth while nothing is plugged in`() = runTest {
        router.connect(BLE_ID) {}
        assertEquals(1, bluetooth.connectCount)
        assertEquals(0, usb.connectCount)
    }

    @Test
    fun `a plugged in ledger is used even for a wallet paired over bluetooth`() = runTest {
        attached = true
        val link = router.connect(BLE_ID) {}
        assertEquals(1, usb.connectCount)
        assertEquals(0, bluetooth.connectCount)
        assertEquals(BLE_ID, link.deviceId)
    }

    @Test
    fun `a wallet paired over usb never scans bluetooth`() = runTest {
        router.connect(USB_ID) {}
        assertEquals(1, usb.connectCount)
        assertEquals(0, bluetooth.connectCount)
    }

    @Test
    fun `a usb link that drops keeps the next connect on usb while the ledger re-enumerates`() = runTest {
        attached = true
        val link = router.connect(BLE_ID) {}
        link.exchange(FakeLedgerDevice.rawApdu(0xb0, 0xa7), timeout = null)
        attached = false
        link.close()
        clock += 3.seconds
        router.connect(BLE_ID) {}
        assertEquals(0, bluetooth.connectCount)
        clock += 6.seconds
        router.connect(BLE_ID) {}
        assertEquals(1, bluetooth.connectCount)
    }

    @Test
    fun `closing a healthy usb link does not pin the wallet to usb`() = runTest {
        attached = true
        router.connect(BLE_ID) {}.close()
        attached = false
        router.connect(BLE_ID) {}
        assertEquals(1, bluetooth.connectCount)
    }

    @Test
    fun `plugging the ledger in while bluetooth is still connecting switches to usb`() = runTest {
        val connecting = StillConnecting()
        val plugged = CompletableDeferred<Unit>()
        val routed = LedgerRoutingTransport(usb, connecting, isUsbAttached = { attached }, awaitUsbAttached = { plugged.await() }, timeSource = clock)
        val result = async { routed.connect(BLE_ID) {} }
        runCurrent()
        assertTrue(connecting.started)
        attached = true
        plugged.complete(Unit)
        val link = result.await()
        assertTrue(connecting.cancelled)
        assertEquals(1, usb.connectCount)
        assertEquals(BLE_ID, link.deviceId)
    }

    @Test
    fun `a bluetooth link that connects first stops waiting for the cable`() = runTest {
        var waiting = false
        val routed = LedgerRoutingTransport(
            usb,
            bluetooth,
            isUsbAttached = { attached },
            awaitUsbAttached = {
                waiting = true
                try {
                    awaitCancellation()
                } finally {
                    waiting = false
                }
            },
            timeSource = clock,
        )
        routed.connect(BLE_ID) {}
        assertEquals(1, bluetooth.connectCount)
        assertEquals(0, usb.connectCount)
        assertFalse(waiting)
    }

    @Test
    fun `a bluetooth failure before the cable is plugged in is reported`() = runTest {
        val bluetoothOff = Unreachable(LedgerFailure.BluetoothOff)
        val routed = LedgerRoutingTransport(usb, bluetoothOff, isUsbAttached = { attached }, awaitUsbAttached = { awaitCancellation() }, timeSource = clock)
        val failure = try {
            routed.connect(BLE_ID) {}
            fail("connect should fail")
            error("unreachable")
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.BluetoothOff, failure)
        assertEquals(0, usb.connectCount)
    }

    @Test
    fun `app switch over usb reconnects over usb even if bluetooth is off`() = runTest {
        val bluetoothOff = Unreachable(LedgerFailure.BluetoothOff)
        val device = FakeLedgerDevice(currentApp = "BOLOS")
        val routed = LedgerRoutingTransport(device, bluetoothOff, isUsbAttached = { attached }, timeSource = clock)
        val session = LedgerSession(routed, BLE_ID, TIMINGS)
        attached = true
        session.currentApp()
        attached = false
        val info = session.ensureApp(LedgerAppId.Ton)
        assertEquals("TON", info.name)
        assertEquals(0, bluetoothOff.connects)
        assertEquals(2, device.connectCount)
    }

    private companion object {
        const val BLE_ID = "C1:2B:3C:4D:5E:6F"
        const val USB_ID = "usb:Stax"
        val TIMINGS = LedgerSessionTimings(
            unlockPollInterval = 10.milliseconds,
            disconnectGrace = 50.milliseconds,
            appLaunchSettle = 1.milliseconds,
            reconnectRetryDelay = 1.milliseconds,
        )
    }
}

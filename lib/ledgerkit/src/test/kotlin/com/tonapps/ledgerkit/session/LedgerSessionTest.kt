package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class LedgerSessionTest {

    private val fastTimings = LedgerSessionTimings(
        unlockPollInterval = 10.milliseconds,
        disconnectGrace = 50.milliseconds,
        appLaunchSettle = 1.milliseconds,
        reconnectRetryDelay = 1.milliseconds,
    )

    @Test
    fun `target app already open needs no switch`() = runTest {
        val device = FakeLedgerDevice(currentApp = "Ethereum")
        val session = LedgerSession(device, "AA:BB", fastTimings)
        val info = session.ensureApp(LedgerAppId.Ethereum)
        assertEquals("Ethereum", info.name)
        assertEquals(1, device.connectCount)
        assertEquals(1, device.sent.size)
    }

    @Test
    fun `dashboard opens app and reconnects after the link drops`() = runTest {
        val device = FakeLedgerDevice(currentApp = "BOLOS")
        val session = LedgerSession(device, "AA:BB", fastTimings)
        val progress = mutableListOf<LedgerProgress>()
        session.reporter = { progress += it }
        val info = session.ensureApp(LedgerAppId.Tron)
        assertEquals("Tron", info.name)
        assertEquals(2, device.connectCount)
        assertTrue(progress.contains(LedgerProgress.OpeningApp(LedgerAppId.Tron)))
        assertTrue(progress.contains(LedgerProgress.Reconnecting))
    }

    @Test
    fun `link dropping before the open app response still ends in the target app`() = runTest {
        val device = FakeLedgerDevice(currentApp = "BOLOS", dropsBeforeOpenResponse = true)
        val session = LedgerSession(device, "AA:BB", fastTimings)
        val info = session.ensureApp(LedgerAppId.Bitcoin)
        assertEquals("Bitcoin", info.name)
        assertEquals(2, device.connectCount)
    }

    @Test
    fun `app switch without disconnect keeps the link`() = runTest {
        val device = FakeLedgerDevice(currentApp = "BOLOS", disconnectsOnAppSwitch = false)
        val session = LedgerSession(device, "AA:BB", fastTimings)
        assertEquals("TON", session.ensureApp(LedgerAppId.Ton).name)
        assertEquals(1, device.connectCount)
    }

    @Test
    fun `another app is quit before opening the target`() = runTest {
        val device = FakeLedgerDevice(currentApp = "Bitcoin")
        val session = LedgerSession(device, "AA:BB", fastTimings)
        val progress = mutableListOf<LedgerProgress>()
        session.reporter = { progress += it }
        assertEquals("Ethereum", session.ensureApp(LedgerAppId.Ethereum).name)
        assertTrue(progress.contains(LedgerProgress.ClosingApp("Bitcoin", LedgerAppId.Ethereum)))
        assertEquals(3, device.connectCount)
    }

    @Test
    fun `locked device is polled until unlocked`() = runTest {
        val device = FakeLedgerDevice(currentApp = "TON", lockedPolls = 3)
        val session = LedgerSession(device, "AA:BB", fastTimings)
        val progress = mutableListOf<LedgerProgress>()
        session.reporter = { progress += it }
        assertEquals("TON", session.ensureApp(LedgerAppId.Ton).name)
        assertEquals(1, progress.count { it == LedgerProgress.WaitingUnlock })
    }

    @Test
    fun `missing app is reported as not installed`() = runTest {
        val device = FakeLedgerDevice(currentApp = "BOLOS", installedApps = setOf("TON"))
        val session = LedgerSession(device, "AA:BB", fastTimings)
        try {
            session.ensureApp(LedgerAppId.Bitcoin)
            fail()
        } catch (e: LedgerFailureException) {
            assertEquals(LedgerFailure.AppNotInstalled(LedgerAppId.Bitcoin), e.failure)
        }
    }

    @Test
    fun `refused app opening is reported as user refusal`() = runTest {
        val device = FakeLedgerDevice(currentApp = "BOLOS", refuseOpen = true)
        val session = LedgerSession(device, "AA:BB", fastTimings)
        try {
            session.ensureApp(LedgerAppId.Ethereum)
            fail()
        } catch (e: LedgerFailureException) {
            assertEquals(LedgerFailure.UserRefused(0x5501), e.failure)
        }
    }

    @Test
    fun `app info parsing`() {
        val info = Bolos.parseAppAndVersion(
            com.tonapps.ledgerkit.apdu.ApduResponse(
                byteArrayOf(1, 5) + "BOLOS".toByteArray() + byteArrayOf(5) + "1.6.0".toByteArray() + byteArrayOf(1, 2),
                0x9000,
            ),
        )
        assertEquals(LedgerAppInfo("BOLOS", "1.6.0"), info)
        assertTrue(info.isDashboard)
        assertTrue(LedgerAppInfo("Ethereum", "1.10.4").isAtLeast("1.9.19"))
        assertTrue(!LedgerAppInfo("TON", "2.0.9").isAtLeast("2.1.0"))
    }
}

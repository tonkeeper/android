package com.tonapps.wallet.features.ledger.progress

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.wallet.features.ledger.common.displayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerProgressViewTest {

    private val sign = LedgerRequest("AA:BB", "Stax", LedgerPurpose.SignTransaction)
    private val verify = LedgerRequest("AA:BB", "Stax", LedgerPurpose.VerifyAddress)

    private fun active(request: LedgerRequest, progress: LedgerProgress) = LedgerOperationState.Active(request, progress)

    private fun start(request: LedgerRequest) = LedgerProgressView(active(request, LedgerProgress.Connecting)).next(active(request, LedgerProgress.Connecting))

    @Test
    fun `connecting shows the bluetooth stage with the status on the device screen`() {
        val view = start(sign)
        assertEquals(LedgerStage.Connect, view.stage)
        assertEquals("Connecting", view.state.displayText)
    }

    @Test
    fun `the app stage names the app the ledger opens and the confirm stage asks to review`() {
        val opening = start(sign).next(active(sign, LedgerProgress.OpeningApp(LedgerAppId.Ethereum)))
        assertEquals(LedgerStage.App, opening.stage)
        assertEquals("Ethereum", opening.state.displayText)
        val unlocking = opening.next(active(sign, LedgerProgress.WaitingUnlock))
        assertEquals(LedgerAppId.Ethereum, unlocking.app)
        assertEquals("Locked", unlocking.state.displayText)
        val confirming = unlocking.next(active(sign, LedgerProgress.Confirm(LedgerAppId.Ethereum, LedgerConfirmKind.Transaction, 2, 3)))
        assertEquals(LedgerStage.Confirm, confirming.stage)
        assertEquals("Review", confirming.state.displayText)
        assertEquals(3, confirming.lastConfirm?.totalSteps)
    }

    @Test
    fun `a failure stays on the stage it happened at`() {
        val failed = start(verify)
            .next(active(verify, LedgerProgress.VerifyAddress(LedgerAppId.Ton, "UQ...")))
            .next(LedgerOperationState.Failed(verify, LedgerFailure.UserRefused(0x6985), canRetry = true))
        assertEquals(LedgerStage.Confirm, failed.stage)
        assertEquals("Rejected", failed.state.displayText)
    }

    @Test
    fun `waiting for the cable and the usb access dialog are the connect stage of a usb link`() {
        val waiting = start(sign).next(active(sign, LedgerProgress.WaitingUsb))
        assertEquals(LedgerStage.Connect, waiting.stage)
        assertTrue(waiting.isUsb)
        val access = waiting.next(active(sign, LedgerProgress.UsbAccess))
        assertEquals(LedgerStage.Connect, access.stage)
        assertTrue(access.isUsb)
        assertEquals(LedgerStage.App, access.next(active(sign, LedgerProgress.OpeningApp(LedgerAppId.Ton))).stage)
    }

    @Test
    fun `the link kind decides the connect step even before any usb progress`() {
        assertFalse(start(sign).isUsb)
        assertTrue(start(sign).copy(linkKind = LedgerLinkKind.Usb).isUsb)
    }

    @Test
    fun `a failure before any progress is a connect failure`() {
        val failed = LedgerProgressView(LedgerOperationState.Failed(sign, LedgerFailure.DeviceNotFound, canRetry = true))
        assertEquals(LedgerStage.Connect, failed.stage)
    }

    @Test
    fun `plugging the ledger in after a bluetooth connect failure retries over usb`() {
        val notFound = LedgerProgressView(LedgerOperationState.Failed(sign, LedgerFailure.DeviceNotFound, canRetry = true))
        val plugged = notFound.copy(linkKind = LedgerLinkKind.Usb)
        assertTrue(plugged.pluggedInAfterConnectFailure(notFound))
        assertFalse(plugged.pluggedInAfterConnectFailure(plugged))
        val final = LedgerProgressView(LedgerOperationState.Failed(sign, LedgerFailure.DeviceNotFound, canRetry = false))
        assertFalse(final.copy(linkKind = LedgerLinkKind.Usb).pluggedInAfterConnectFailure(final))
        val refused = start(sign)
            .next(active(sign, LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Transaction)))
            .next(LedgerOperationState.Failed(sign, LedgerFailure.UserRefused(0x6985), canRetry = true))
        assertFalse(refused.copy(linkKind = LedgerLinkKind.Usb).pluggedInAfterConnectFailure(refused))
    }

    @Test
    fun `done keeps the last screen and a new request starts from scratch`() {
        val done = start(sign)
            .next(active(sign, LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Transaction)))
            .next(LedgerOperationState.Done(sign))
        assertNull(done.stage)
        assertEquals("Done", done.state.displayText)
        val next = done.next(active(verify, LedgerProgress.Connecting))
        assertNull(next.app)
        assertNull(next.lastConfirm)
        assertEquals(LedgerStage.Connect, next.stage)
    }
}

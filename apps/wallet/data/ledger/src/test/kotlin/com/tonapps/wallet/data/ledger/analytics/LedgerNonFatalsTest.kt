package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.core.IssueHelper
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerOperationOutcome
import com.tonapps.ledgerkit.controller.LedgerOperationResult
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.signing.LedgerDismissedException
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LedgerNonFatalsTest {

    private val recorded = ArrayList<Pair<Throwable, Map<String, String>>>()

    @Before
    fun setUp() {
        mockkObject(IssueHelper)
        every { IssueHelper.recordException(any(), any<Map<String, String>>()) } answers { recorded += firstArg<Throwable>() to secondArg() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `device failures the user can act on are not issues`() {
        val expected = listOf(
            LedgerFailure.UserRefused(USER_REFUSED),
            LedgerFailure.DeviceLocked,
            LedgerFailure.AppNotInstalled(LedgerAppId.Solana),
            LedgerFailure.ConnectionLost,
            LedgerFailure.Unsupported("Bitcoin swaps with a provider PSBT"),
        )

        expected.forEach { failure -> assertFalse(failure.toString(), LedgerFailureException(failure).isReportableLedgerIssue()) }
    }

    @Test
    fun `unexpected answers and rejected payloads are issues`() {
        assertTrue(LedgerFailureException(LedgerFailure.Unexpected("Truncated Tron address")).isReportableLedgerIssue())
        assertTrue(LedgerFailureException(LedgerFailure.InvalidPayload(0x6a80, "Invalid data")).isReportableLedgerIssue())
        assertTrue(IllegalStateException("No Ledger wallets were created").isReportableLedgerIssue())
    }

    @Test
    fun `failures already on the error prompt, cancellations and network errors are skipped`() {
        assertFalse(LedgerFailureException(LedgerFailure.Unexpected("Framing error"), isPresented = true).isReportableLedgerIssue())
        assertFalse(LedgerDismissedException(LedgerFailure.Unexpected("Framing error")).isReportableLedgerIssue())
        assertFalse(CancellationException().isReportableLedgerIssue())
        assertFalse(IllegalStateException("wrapped", IOException("timeout")).isReportableLedgerIssue())
        assertFalse(LedgerFailureException(LedgerFailure.InvalidPayload(0, "Unable to build"), cause = IOException("timeout")).isReportableLedgerIssue())
    }

    @Test
    fun `an issue is recorded once with its site`() {
        val error = IllegalStateException("Ledger import payload is not set")

        LedgerNonFatals.recordIssue(error, LedgerIssueSite.Import)
        LedgerNonFatals.recordIssue(error, LedgerIssueSite.Pairing)

        assertEquals(listOf<Pair<Throwable, Map<String, String>>>(error to mapOf("ledger_site" to "import")), recorded)
    }

    @Test
    fun `a wrapped call records its issue and rethrows it`() = runTest {
        val error = IllegalStateException("Wallet is not paired with a Ledger")

        val thrown = runCatching { reportingLedgerIssues(LedgerIssueSite.Signing) { throw error } }.exceptionOrNull()

        assertSame(error, thrown)
        assertEquals(mapOf("ledger_site" to "signing"), recorded.single().second)
    }

    @Test
    fun `a request records each distinct device issue with the request context`() {
        val unexpected = LedgerFailureException(LedgerFailure.Unexpected("Truncated Ethereum signature"))
        val result = LedgerOperationResult(
            request = LedgerRequest("AA:BB", "Stax", LedgerPurpose.SignTransaction),
            outcome = LedgerOperationOutcome.Failed,
            attempts = 3,
            durationMs = 4_200,
            lastFailure = unexpected.failure,
            errors = listOf(
                LedgerFailureException(LedgerFailure.DeviceLocked),
                unexpected,
                LedgerFailureException(LedgerFailure.Unexpected("Truncated Ethereum signature")),
            ),
            app = LedgerAppId.Ethereum,
            model = LedgerModel.Stax,
            linkKind = LedgerLinkKind.Bluetooth,
        )

        LedgerNonFatals.reportRequest(result)

        assertSame(unexpected, recorded.single().first)
        assertEquals(
            mapOf(
                "ledger_purpose" to "sign_transaction",
                "ledger_attempts" to "3",
                "ledger_app" to "ethereum",
                "ledger_model" to "stax",
                "ledger_transport" to "bluetooth",
            ),
            recorded.single().second,
        )
    }

    private companion object {
        const val USER_REFUSED = 0x6985
    }
}

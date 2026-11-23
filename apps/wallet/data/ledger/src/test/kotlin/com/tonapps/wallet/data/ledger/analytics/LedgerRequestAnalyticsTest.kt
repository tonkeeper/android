package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationOutcome
import com.tonapps.ledgerkit.controller.LedgerOperationResult
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LedgerRequestAnalyticsTest {

    private val tracked = ArrayList<Pair<String, Map<String, Any>>>()
    private val analytics = LedgerRequestAnalytics(LedgerController(mockk(relaxed = true), TestScope()), TestScope())

    @Before
    fun setUp() {
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
        unmockkAll()
    }

    private fun result(
        outcome: LedgerOperationOutcome,
        lastFailure: LedgerFailure? = null,
        attempts: Int = 1,
        app: LedgerAppId? = LedgerAppId.Ethereum,
    ) = LedgerOperationResult(
        request = LedgerRequest("AA:BB", "Stax", LedgerPurpose.SignTransaction),
        outcome = outcome,
        attempts = attempts,
        durationMs = 4_200,
        lastFailure = lastFailure,
        errors = emptyList(),
        app = app,
        model = LedgerModel.Stax,
        linkKind = LedgerLinkKind.Usb,
    )

    private fun props(): Map<String, Any> = tracked.single { it.first == "ledger_request_result" }.second

    @Test
    fun `a signed request reports its purpose, device, link and app`() {
        analytics.track(result(LedgerOperationOutcome.Success))

        assertEquals(
            mapOf(
                "purpose" to "sign_transaction",
                "outcome" to "success",
                "attempts" to 1,
                "duration_ms" to 4_200,
                "ledger_app" to "ethereum",
                "ledger_model" to "stax",
                "ledger_transport" to "usb",
            ),
            props(),
        )
    }

    @Test
    fun `a request declined on the device is a rejection with the device status`() {
        analytics.track(result(LedgerOperationOutcome.Failed, LedgerFailure.UserRefused(USER_REFUSED)))

        val props = props()
        assertEquals("rejected", props["outcome"])
        assertEquals("user_refused", props["failure_type"])
        assertEquals(USER_REFUSED, props["error_code"])
    }

    @Test
    fun `a request signed after turning on blind signing keeps the failure it got past`() {
        val blindSigning = LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning)
        analytics.track(result(LedgerOperationOutcome.Success, blindSigning, attempts = 2))

        val props = props()
        assertEquals("success", props["outcome"])
        assertEquals(2, props["attempts"])
        assertEquals("setting_required", props["failure_type"])
        assertEquals("blind_signing", props["ledger_setting"])
        assertEquals("ton", props["ledger_app"])
    }

    @Test
    fun `a request closed in the app is a cancellation`() {
        analytics.track(result(LedgerOperationOutcome.Cancelled, app = null))

        assertEquals("cancelled", props()["outcome"])
    }

    private companion object {
        const val USER_REFUSED = 0x6985
    }
}

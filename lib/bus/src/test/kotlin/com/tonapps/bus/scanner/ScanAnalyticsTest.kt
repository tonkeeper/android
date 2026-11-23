package com.tonapps.bus.scanner

import com.tonapps.bus.core.UtmEventExecutor
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.Scanner.ScannerErrorType
import com.tonapps.bus.generated.Events.Scanner.ScannerInput
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanAnalyticsTest {

    private class RecordingExecutor : EventExecutor {
        val events = ArrayList<Pair<String, Map<String, Any>>>()

        override fun trackEvent(eventName: String, props: Map<String, Any>) {
            events += eventName to props
        }
    }

    private val recording = RecordingExecutor()
    private val analytics = ScanAnalytics { utm -> DefaultEvents(UtmEventExecutor(recording, utm)) }

    @Test
    fun openCarriesTheEntryUtmAndNothingElse() {
        analytics.open(UtmParameters(source = "widget", content = "scan"))

        assertEquals(
            listOf("scan_open" to mapOf("utm_source" to "widget", "utm_content" to "scan")),
            recording.events,
        )
    }

    @Test
    fun openFromInsideTheAppHasNoUtm() {
        analytics.open(UtmParameters.Empty)

        assertEquals(listOf("scan_open" to emptyMap<String, Any>()), recording.events)
    }

    @Test
    fun successIsTaggedWithTheScannedLinkUtmNotTheEntryOne() {
        analytics.open(UtmParameters(source = "widget"))
        analytics.success("$TRANSFER?amount=1&utm_source=poster&utm_medium=qr", ScannerInput.Camera)

        assertEquals(
            "scan_success" to mapOf("input" to "camera", "utm_source" to "poster", "utm_medium" to "qr"),
            recording.events.last(),
        )
    }

    @Test
    fun bareAddressSuccessHasNoUtm() {
        analytics.success(ADDRESS, ScannerInput.Gallery)

        assertEquals(listOf("scan_success" to mapOf("input" to "gallery")), recording.events)
    }

    @Test
    fun sameCodeIsReportedOncePerOpenAcrossSuccessAndError() {
        analytics.success(ADDRESS, ScannerInput.Camera)
        analytics.success(ADDRESS, ScannerInput.Camera)
        analytics.error(ADDRESS, ScannerInput.Gallery, ScannerErrorType.InvalidLink)
        analytics.error("garbage", ScannerInput.Camera, ScannerErrorType.InvalidLink)
        analytics.error("garbage", ScannerInput.Camera, ScannerErrorType.InvalidLink)
        analytics.success("garbage", ScannerInput.Camera)

        assertEquals(listOf("scan_success", "scan_error"), recording.events.map { it.first })
    }

    @Test
    fun differentCodesAreReportedSeparately() {
        analytics.success(ADDRESS, ScannerInput.Camera)
        analytics.success(TRANSFER, ScannerInput.Camera)

        assertEquals(2, recording.events.size)
    }

    @Test
    fun errorCarriesInputAndErrorTypeWithoutTheLinkUtm() {
        analytics.error("$TRANSFER?utm_source=poster", ScannerInput.Gallery, ScannerErrorType.UnsupportedHere)

        assertEquals(
            listOf("scan_error" to mapOf("input" to "gallery", "error_type" to "unsupported_here")),
            recording.events,
        )
    }

    @Test
    fun noCodeIsReportedForEveryPick() {
        analytics.noCode(ScannerInput.Gallery)
        analytics.noCode(ScannerInput.Gallery)

        assertEquals(
            List(2) { "scan_error" to mapOf("input" to "gallery", "error_type" to "no_code") },
            recording.events,
        )
    }

    private companion object {
        const val ADDRESS = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"
        const val TRANSFER = "tonkeeper://transfer/$ADDRESS"
    }
}

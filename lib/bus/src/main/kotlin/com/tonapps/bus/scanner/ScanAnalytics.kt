package com.tonapps.bus.scanner

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.Scanner.ScannerErrorType
import com.tonapps.bus.generated.Events.Scanner.ScannerInput

/**
 * The events of one scanner open, used from the main thread. A read code is reported once, whichever way
 * it ends; a picked image without a code has nothing to dedupe on and is reported every time.
 */
class ScanAnalytics(private val taggedEvents: (UtmParameters) -> DefaultEvents) {

    private val reported = HashSet<String>()

    /** [entryUtm] belongs to what opened the scanner from outside the app; Empty when opened in-app. */
    fun open(entryUtm: UtmParameters) {
        taggedEvents(entryUtm).scanner.scanOpen()
    }

    /** The app opened what [code] points to; tagged with the scanned link's own utm. */
    fun success(code: String, input: ScannerInput) {
        if (reported.add(code)) {
            taggedEvents(UtmParameters.fromLink(code)).scanner.scanSuccess(input)
        }
    }

    fun error(code: String, input: ScannerInput, type: ScannerErrorType) {
        if (reported.add(code)) {
            taggedEvents(UtmParameters.Empty).scanner.scanError(input, type)
        }
    }

    fun noCode(input: ScannerInput) {
        taggedEvents(UtmParameters.Empty).scanner.scanError(input, ScannerErrorType.NoCode)
    }

    companion object {
        fun default(): ScanAnalytics = ScanAnalytics(AnalyticsHelper.Default::eventsTaggedWith)
    }
}

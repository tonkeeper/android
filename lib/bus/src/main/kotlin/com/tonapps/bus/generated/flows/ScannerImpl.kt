package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Scanner.ScannerErrorType
import com.tonapps.bus.generated.Events.Scanner.ScannerInput

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class ScannerImpl(
    private val eventExecutor: EventExecutor,
) : Events.Scanner {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * scan_open
     *
     * The QR scanner opened, before any code is read. When something outside the app opened it — on iOS the scan button of a widget — it carries that entry's `utm_*`; opened from inside the app, `utm_*` are NULL. The scanned link's own tags arrive later, on `scan_success`. Together with `scan_success` / `scan_error` it gives the share of opened scanners that end in an opened code.

     */
    @AnyThread
    override fun scanOpen() {
        trackEvent("scan_open", emptyMap())
    }

    /**
     * scan_success
     *
     * The QR scanner read a code and the app opened what it points to: an address, a transfer, a TON Connect or WalletConnect request, a dapp or any other link the app handles. Sent once per read code; the same code read again while the scanner stays open is not reported again. It is the entry event of a scan: it carries the scanned link's own `utm_*` (e.g. a QR on a poster), not those of `scan_open`, and the flow the scan opens carries them on its events, next to its `from: qr_code`. NULL `utm_*` when the code carried none, as a bare address does. Flow drawn in `scanner.md`.

     */
    @AnyThread
    override fun scanSuccess(input: ScannerInput) {
        trackEvent("scan_success", hashMapOf("input" to input.key))
    }

    /**
     * scan_error
     *
     * The QR scanner read a code it could not open. Sent once per read code, like `scan_success`; repeats of the same code while the scanner stays open are not reported again.

     */
    @AnyThread
    override fun scanError(input: ScannerInput, errorType: ScannerErrorType) {
        trackEvent("scan_error", hashMapOf("input" to input.key, "error_type" to errorType.key))
    }
}

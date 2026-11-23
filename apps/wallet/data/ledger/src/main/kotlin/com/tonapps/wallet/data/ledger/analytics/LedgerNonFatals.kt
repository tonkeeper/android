package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.core.IssueHelper
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerFallbackException
import com.tonapps.ledgerkit.controller.LedgerOperationResult
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap

enum class LedgerIssueSite(val key: String) {
    Signing("signing"),
    Discovery("discovery"),
    Wallet("wallet"),
    Connection("connection"),
    Pairing("pairing"),
    DeviceScreen("device_screen"),
    Import("import"),
    Migration("migration"),
    ClearSigning("clear_signing"),
    Storage("storage"),
}

object LedgerNonFatals {

    private val recorded: MutableSet<Throwable> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    fun report(error: Throwable, site: LedgerIssueSite) {
        L.w(error)
        recordIssue(error, site)
    }

    fun recordIssue(error: Throwable, site: LedgerIssueSite) {
        if (error.isReportableLedgerIssue()) {
            record(error, mapOf(KEY_SITE to site.key))
        }
    }

    fun recordFallback(issue: Throwable) {
        L.w(issue)
        record(issue, mapOf(KEY_SITE to LedgerIssueSite.ClearSigning.key))
    }

    internal fun reportFallback(message: String, cause: Throwable) {
        recordFallback(LedgerFallbackException(message, cause))
    }

    internal fun reportRequest(result: LedgerOperationResult) {
        val keys = buildMap {
            put(KEY_PURPOSE, result.request.purpose.toLedgerFlowPurpose().key)
            put(KEY_ATTEMPTS, result.attempts.toString())
            result.app?.let { put(KEY_APP, it.toLedgerFlowApp().key) }
            result.model?.let { put(KEY_MODEL, it.toLedgerFlowModel().key) }
            result.linkKind?.let { put(KEY_TRANSPORT, it.toLedgerFlowTransport().key) }
        }
        result.errors
            .filter { it.isReportableLedgerIssue() }
            .distinctBy { it.failure }
            .forEach { error -> record(error, keys) }
    }

    private fun record(error: Throwable, keys: Map<String, String>) {
        if (recorded.add(error)) {
            IssueHelper.recordException(error, keys)
        }
    }
}

val LedgerFailure.isReportable: Boolean
    get() = when (this) {
        is LedgerFailure.Unexpected,
        is LedgerFailure.InvalidPayload,
        is LedgerFailure.DeviceError,
        is LedgerFailure.WrongAppOpen,
        LedgerFailure.UnsupportedDevice -> true
        LedgerFailure.Cancelled,
        LedgerFailure.BluetoothUnavailable,
        LedgerFailure.BluetoothOff,
        LedgerFailure.NoPermission,
        LedgerFailure.DeviceNotFound,
        LedgerFailure.UsbNotConnected,
        LedgerFailure.UsbAccessDenied,
        LedgerFailure.ScanUnavailable,
        LedgerFailure.PairingRefused,
        LedgerFailure.PairingLost,
        LedgerFailure.ConnectionLost,
        LedgerFailure.Busy,
        LedgerFailure.DeviceLocked,
        LedgerFailure.RequestExpired,
        is LedgerFailure.Timeout,
        is LedgerFailure.AppNotInstalled,
        is LedgerFailure.UserRefused,
        is LedgerFailure.SettingRequired,
        is LedgerFailure.AppUpdateRequired,
        is LedgerFailure.AddressMismatch,
        is LedgerFailure.Unsupported -> false
    }

internal inline fun <T> reportingLedgerIssues(site: LedgerIssueSite, block: () -> T): T = try {
    block()
} catch (e: Throwable) {
    LedgerNonFatals.recordIssue(e, site)
    throw e
}

internal fun Throwable.isReportableLedgerIssue(): Boolean = when {
    this is CancellationException -> false
    generateSequence(this) { it.cause }.any { it is IOException } -> false
    this is LedgerFailureException -> !isPresented && failure.isReportable
    else -> true
}

private const val KEY_SITE = "ledger_site"
private const val KEY_PURPOSE = "ledger_purpose"
private const val KEY_ATTEMPTS = "ledger_attempts"
private const val KEY_APP = "ledger_app"
private const val KEY_MODEL = "ledger_model"
private const val KEY_TRANSPORT = "ledger_transport"

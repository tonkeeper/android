package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDeviceViewFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDiscoveryErrorFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowFailureType
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerApp
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerModel
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerSetting
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowLedgerTransport
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowOutcome
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowPurpose
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class LedgerFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.LedgerFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * ledger_request_result
     *
     * A request to the Ledger ended — sent exactly once per request, whatever screen asked for it, so the success rate, the time a hardware step takes and the reasons it fails can be read per purpose, model, transport and app. A request the user retried after a failure is still one request: attempts counts the tries, and failure_type keeps the failure of the last failed try even when a retry then succeeded, which is how "had to turn on Blind signing first" shows up. duration_ms runs from the request to its end, the time the user spent confirming on the device and on the retry prompt included. A request refused at once because the wallet was still talking to the Ledger is not sent. Multichain Ledger only.

     */
    @AnyThread
    override fun ledgerRequestResult(
        purpose: LedgerFlowPurpose,
        outcome: LedgerFlowOutcome,
        attempts: Int,
        durationMs: Int,
        failureType: LedgerFlowFailureType?,
        ledgerApp: LedgerFlowLedgerApp?,
        ledgerSetting: LedgerFlowLedgerSetting?,
        errorCode: Int?,
        ledgerModel: LedgerFlowLedgerModel?,
        ledgerTransport: LedgerFlowLedgerTransport?
    ) {
        val props = hashMapOf<String, Any>(
            "purpose" to purpose.key,
            "outcome" to outcome.key,
            "attempts" to attempts,
            "duration_ms" to durationMs
        )
        failureType?.let { props["failure_type"] = it.key }
        ledgerApp?.let { props["ledger_app"] = it.key }
        ledgerSetting?.let { props["ledger_setting"] = it.key }
        errorCode?.let { props["error_code"] = it }
        ledgerModel?.let { props["ledger_model"] = it.key }
        ledgerTransport?.let { props["ledger_transport"] = it.key }
        trackEvent("ledger_request_result", props)
    }

    /**
     * ledger_device_view
     *
     * The Ledger device screen of a multichain Ledger wallet opened: the connection status and check, Add networks, the addresses to verify, and the recent requests. from tells which entry point it was, the connection indicator in the wallet header among them.

     */
    @AnyThread
    override fun ledgerDeviceView(from: LedgerDeviceViewFrom) {
        trackEvent("ledger_device_view", hashMapOf("from" to from.key))
    }

    /**
     * ledger_add_networks_start
     *
     * The user tapped Add networks on the Ledger device screen: the wallet reads the apps installed on the Ledger after pairing and adds their networks. Ends in ledger_add_networks_success or ledger_add_networks_error.

     */
    @AnyThread
    override fun ledgerAddNetworksStart() {
        trackEvent("ledger_add_networks_start", emptyMap())
    }

    /**
     * ledger_add_networks_success
     *
     * Add networks finished. added_apps lists the apps whose networks were added; it is empty when the Ledger had no app the wallet was missing.

     */
    @AnyThread
    override fun ledgerAddNetworksSuccess(addedApps: String, ledgerModel: LedgerFlowLedgerModel?) {
        val props = hashMapOf<String, Any>("added_apps" to addedApps)
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("ledger_add_networks_success", props)
    }

    /**
     * ledger_add_networks_error
     *
     * Add networks failed and nothing was added. failure_type is set when the device failed; it is absent when the Ledger answered but the new addresses could not be saved (for example, the backend did not accept them), so such a failure has no ledger_request_result with a failure. A request the user closes in the app sends nothing.

     */
    @AnyThread
    override fun ledgerAddNetworksError(
        failureType: LedgerFlowFailureType?,
        ledgerModel: LedgerFlowLedgerModel?
    ) {
        val props = mutableMapOf<String, Any>()
        failureType?.let { props["failure_type"] = it.key }
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("ledger_add_networks_error", props)
    }

    /**
     * ledger_migration_tooltip_view
     *
     * The tooltip offering to add Ethereum, Bitcoin, Solana and the other networks was shown on the main screen of a TON-only Ledger wallet. Sent each time it is shown: at most once a day, and never again once the user closed it.

     */
    @AnyThread
    override fun ledgerMigrationTooltipView() {
        trackEvent("ledger_migration_tooltip_view", emptyMap())
    }

    /**
     * ledger_migration_start
     *
     * The user opened the migration of a TON-only Ledger wallet to a multichain one: the pairing screen that connects the same Ledger and reads the other apps' addresses. A migration does not send the wallet_import_* events.

     */
    @AnyThread
    override fun ledgerMigrationStart(from: LedgerMigrationStartFrom) {
        trackEvent("ledger_migration_start", hashMapOf("from" to from.key))
    }

    /**
     * ledger_migration_success
     *
     * A multichain Ledger wallet replaced a TON-only one: the TON-only wallet was removed and its selection and push notifications moved to the new wallet. from = tooltip / settings ends a ledger_migration_start; import and startup are replacements without the migration screens, see LedgerMigrationSource. The TON-only wallet is kept, and this event is not sent, while its push notifications cannot be turned off; it is replaced on a later start.

     */
    @AnyThread
    override fun ledgerMigrationSuccess(from: LedgerMigrationStartFrom, ledgerModel: LedgerFlowLedgerModel?) {
        val props = hashMapOf<String, Any>("from" to from.key)
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("ledger_migration_success", props)
    }

    /**
     * ledger_migration_error
     *
     * A migration attempt failed and the pairing screen went back to the device search, where the user can try again. failure_type = address_mismatch is a Ledger that does not hold the wallet — another device or another recovery phrase. A user who leaves the screen sends nothing.

     */
    @AnyThread
    override fun ledgerMigrationError(
        from: LedgerMigrationStartFrom,
        failureType: LedgerFlowFailureType,
        ledgerModel: LedgerFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>("from" to from.key, "failure_type" to failureType.key)
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("ledger_migration_error", props)
    }

    /**
     * ledger_discovery_error
     *
     * An app of the Ledger was skipped while the wallet read the accounts of its apps, so the networks of that app are not added. Sent once for every skipped app each time the accounts are read; the apps that were read are ledger_apps on wallet_import_ledger_accounts_view and added_apps on ledger_add_networks_success. Reading addresses needs no app setting, so failure_type is app_not_installed, app_update_required or user_refused. The TON app is never skipped: a failure there stops the reading.

     */
    @AnyThread
    override fun ledgerDiscoveryError(
        from: LedgerDiscoveryErrorFrom,
        ledgerApp: LedgerFlowLedgerApp,
        failureType: LedgerFlowFailureType,
        ledgerModel: LedgerFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "ledger_app" to ledgerApp.key,
            "failure_type" to failureType.key
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("ledger_discovery_error", props)
    }
}

package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationAnswer
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationCheckOutcome
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationConfirmClickAction
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationFrom
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationImportResultOutcome
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationNotMineClickAction
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationOfferClickAction
import com.tonapps.bus.generated.Events.DomainMigration.DomainMigrationPopupResultOutcome

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DomainMigrationImpl(
    private val eventExecutor: EventExecutor,
) : Events.DomainMigration {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * domain_migration_check
     *
     * Sent by the web wallet on its new domain when a browser profile with no wallets there, and not asked before, looks for wallets left on the old domain, before the browser leaves for the old domain. Profiles with wallets, profiles already asked and crawlers do not check and send nothing. A check that was not sent (`unreachable`, `failed`) does not mark the profile as asked, so it runs, and this event is sent, again on every visit until one is sent: count users, not events.

     */
    @AnyThread
    override fun domainMigrationCheck(outcome: DomainMigrationCheckOutcome) {
        trackEvent("domain_migration_check", hashMapOf("outcome" to outcome.key))
    }

    /**
     * domain_migration_check_answer
     *
     * Sent when the browser comes back from the old domain with its answer to `domain_migration_check`, so only after one with `outcome = sent`. The old domain's "Move my wallets" button does not send it.

     */
    @AnyThread
    override fun domainMigrationCheckAnswer(answer: DomainMigrationAnswer) {
        trackEvent("domain_migration_check_answer", hashMapOf("answer" to answer.key))
    }

    /**
     * domain_migration_offer_view
     *
     * Sent when the offer to move wallets from the old domain opens. Once per page load.

     */
    @AnyThread
    override fun domainMigrationOfferView(from: DomainMigrationFrom) {
        trackEvent("domain_migration_offer_view", hashMapOf("from" to from.key))
    }

    /**
     * domain_migration_offer_click
     *
     * Sent when the user answers the offer to move wallets.
     */
    @AnyThread
    override fun domainMigrationOfferClick(action: DomainMigrationOfferClickAction) {
        trackEvent("domain_migration_offer_click", hashMapOf("action" to action.key))
    }

    /**
     * domain_migration_popup_result
     *
     * Sent when the old domain's popup ends, once per popup. A popup is also opened by "Try again" after `rejected`, so there can be more results than `domain_migration_offer_click` with `continue`.

     */
    @AnyThread
    override fun domainMigrationPopupResult(outcome: DomainMigrationPopupResultOutcome) {
        trackEvent("domain_migration_popup_result", hashMapOf("outcome" to outcome.key))
    }

    /**
     * domain_migration_confirm_view
     *
     * Sent when the list of arrived wallets is shown for the user to confirm before anything is written. Not sent when every arrived account is already on the new domain: the flow then goes straight to `domain_migration_import_result` with `already_here`.

     */
    @AnyThread
    override fun domainMigrationConfirmView(accountsCount: Int, alreadyHereAccountsCount: Int) {
        trackEvent("domain_migration_confirm_view", hashMapOf("accounts_count" to accountsCount, "already_here_accounts_count" to alreadyHereAccountsCount))
    }

    /**
     * domain_migration_confirm_click
     *
     * Sent when the user answers the list of arrived wallets.
     */
    @AnyThread
    override fun domainMigrationConfirmClick(action: DomainMigrationConfirmClickAction) {
        trackEvent("domain_migration_confirm_click", hashMapOf("action" to action.key))
    }

    /**
     * domain_migration_not_mine_click
     *
     * Sent when the user picks what to do after saying the arrived wallets are not theirs.

     */
    @AnyThread
    override fun domainMigrationNotMineClick(action: DomainMigrationNotMineClickAction) {
        trackEvent("domain_migration_not_mine_click", hashMapOf("action" to action.key))
    }

    /**
     * domain_migration_import_result
     *
     * Sent when moving wallets from the old domain ends. `uuid_persistent` on this event is the new domain's own id, `legacy_user_id` the old domain's; after a successful import of a profile that had no wallets on the new domain, the new domain takes over the old id, so later events carry `legacy_user_id` as their `uuid_persistent`.

     */
    @AnyThread
    override fun domainMigrationImportResult(
        outcome: DomainMigrationImportResultOutcome,
        accountsCount: Int,
        legacyUserId: String?
    ) {
        val props = hashMapOf<String, Any>("outcome" to outcome.key, "accounts_count" to accountsCount)
        legacyUserId?.let { props["legacy_user_id"] = it }
        trackEvent("domain_migration_import_result", props)
    }
}

package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Migration.MigrationChain
import com.tonapps.bus.generated.Events.Migration.MigrationFailedPart
import com.tonapps.bus.generated.Events.Migration.MigrationFeeAsset
import com.tonapps.bus.generated.Events.Migration.MigrationFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class MigrationImpl(
    private val eventExecutor: EventExecutor,
) : Events.Migration {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * migration_start
     *
     * User entered the migration flow
     */
    @AnyThread
    override fun migrationStart(from: MigrationFrom) {
        trackEvent("migration_start", hashMapOf("from" to from.key))
    }

    /**
     * migrate_wallet_selection_view
     *
     * The migration wallet picker ("Choose wallets to migrate") listed at least one wallet. Emitted when the list renders, not when the screen opens: a user with no legacy TON wallet holding assets, or a failed load, sees the screen but no picker and emits nothing. So the gap between migration_start and this event is "had nothing to migrate", while the gap between this event and migrate_wallet_selection_click is a real choice not made.

     */
    @AnyThread
    override fun migrateWalletSelectionView() {
        trackEvent("migrate_wallet_selection_view", emptyMap())
    }

    /**
     * migrate_wallet_selection_click
     *
     * User tapped "Continue" on the wallet/asset selection screen
     */
    @AnyThread
    override fun migrateWalletSelectionClick() {
        trackEvent("migrate_wallet_selection_click", emptyMap())
    }

    /**
     * migrate_transaction_confirmation_view
     *
     * User sees the confirmation screen for the migration transaction
     */
    @AnyThread
    override fun migrateTransactionConfirmationView(feeAsset: MigrationFeeAsset) {
        trackEvent("migrate_transaction_confirmation_view", hashMapOf("fee_asset" to feeAsset.key))
    }

    /**
     * migrate_transaction_success
     *
     * User sent the migration transaction successfully
     */
    @AnyThread
    override fun migrateTransactionSuccess(feeAsset: MigrationFeeAsset) {
        trackEvent("migrate_transaction_success", hashMapOf("fee_asset" to feeAsset.key))
    }

    /**
     * migrate_transaction_error
     *
     * User failed to send the migration transaction. Error fields use the same type/code/message as the op_terminal event for consistent grouping.

     */
    @AnyThread
    override fun migrateTransactionError(
        feeAsset: MigrationFeeAsset,
        failedPart: MigrationFailedPart,
        isPartial: Boolean,
        errorType: String?,
        errorCode: Int?,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "fee_asset" to feeAsset.key,
            "failed_part" to failedPart.key,
            "is_partial" to isPartial
        )
        errorType?.let { props["error_type"] = it }
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        trackEvent("migrate_transaction_error", props)
    }

    /**
     * migrate_transaction_prepare_error
     *
     * Preparing the migration failed, so the confirmation screen could not be shown in a confirmable state. Complements migrate_transaction_error, which only covers failures of the send itself: together with migrate_transaction_confirmation_view this accounts for every user who reached the confirmation screen. Preparation runs per chain, so one visit can emit one event per chain, and retrying the screen re-emits. Error fields use the same type/code/message as the op_terminal event for consistent grouping.
Only chains that actually hold assets are prepared: a wallet with no TRON assets never emits chain = tron. A missing event means the chain was absent or prepared fine — never that it silently failed. To tell a one-chain wallet from a two-chain wallet where both chains failed, count the events in the visit, not their absence.

     */
    @AnyThread
    override fun migrateTransactionPrepareError(
        chain: MigrationChain,
        isBlocking: Boolean,
        errorType: String?,
        errorCode: Int?,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>("chain" to chain.key, "is_blocking" to isBlocking)
        errorType?.let { props["error_type"] = it }
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        trackEvent("migrate_transaction_prepare_error", props)
    }
}

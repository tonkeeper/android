package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class EarnFlowExternalImpl(
    private val eventExecutor: EventExecutor,
) : Events.EarnFlowExternal {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * earn_webview_screen_view
     *
     * Fired every time the page shows another screen, the first one after the page loads included.

     */
    @AnyThread
    override fun earnWebviewScreenView(isExternalWebView: Int, eventProvider: String, screenName: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "screen_name" to screenName
        )
        trackEvent("earn_webview_screen_view", props)
    }

    /**
     * earn_vault_list_viewed
     *
     * Fired when the page shows the vault list, and again when a filter or a sort changes what it shows.

     */
    @AnyThread
    override fun earnVaultListViewed(
        isExternalWebView: Int,
        eventProvider: String,
        vaultsCount: Int,
        chainsShown: String,
        assetsShown: String
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vaults_count" to vaultsCount,
            "chains_shown" to chainsShown,
            "assets_shown" to assetsShown
        )
        trackEvent("earn_vault_list_viewed", props)
    }

    /**
     * earn_vault_filter_applied
     *
     * Fired when the user applies a filter to the vault list.
     */
    @AnyThread
    override fun earnVaultFilterApplied(isExternalWebView: Int, eventProvider: String, filterType: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "filter_type" to filterType
        )
        trackEvent("earn_vault_filter_applied", props)
    }

    /**
     * earn_vault_sort_applied
     *
     * Fired when the user sorts the vault list.
     */
    @AnyThread
    override fun earnVaultSortApplied(isExternalWebView: Int, eventProvider: String, sortBy: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "sort_by" to sortBy
        )
        trackEvent("earn_vault_sort_applied", props)
    }

    /**
     * earn_vault_selected
     *
     * Fired when a vault opens: the user opens it from the list, or the page opens right on its `/earn/{network}/{vaultId}` route, the way the wallet's Earn entry points — a carousel card, a position row, an asset's APY badge — open it.

     */
    @AnyThread
    override fun earnVaultSelected(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        isInitialRoute: Boolean,
        asset: String,
        apy: Double,
        tvlUsd: Double,
        riskScore: Double?
    ) {
        val props = hashMapOf<String, Any>(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "is_initial_route" to isInitialRoute,
            "asset" to asset,
            "apy" to apy,
            "tvl_usd" to tvlUsd
        )
        riskScore?.let { props["risk_score"] = it }
        trackEvent("earn_vault_selected", props)
    }

    /**
     * earn_deposit_started
     *
     * Fired when the user opens the deposit form of a vault.
     */
    @AnyThread
    override fun earnDepositStarted(isExternalWebView: Int, eventProvider: String, vaultId: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId
        )
        trackEvent("earn_deposit_started", props)
    }

    /**
     * earn_deposit_amount_entered
     *
     * Fired when the user continues from the deposit form with an amount — once per continue, not on every keystroke.

     */
    @AnyThread
    override fun earnDepositAmountEntered(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        amountUsd: Double,
        isMax: Boolean
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "amount_usd" to amountUsd,
            "is_max" to isMax
        )
        trackEvent("earn_deposit_amount_entered", props)
    }

    /**
     * earn_deposit_review_shown
     *
     * Fired when the page shows the review of a deposit.
     */
    @AnyThread
    override fun earnDepositReviewShown(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        estimatedGasUsd: Double,
        estimatedApy: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "estimated_gas_usd" to estimatedGasUsd,
            "estimated_apy" to estimatedApy
        )
        trackEvent("earn_deposit_review_shown", props)
    }

    /**
     * earn_deposit_confirm_tapped
     *
     * Fired when the user confirms a deposit on the review; the page then asks the wallet to sign (earn_sign_request), and the deposit ends in earn_deposit_success or earn_deposit_failed.

     */
    @AnyThread
    override fun earnDepositConfirmTapped(isExternalWebView: Int, eventProvider: String, vaultId: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId
        )
        trackEvent("earn_deposit_confirm_tapped", props)
    }

    /**
     * earn_deposit_success
     *
     * Fired once the deposit's last transaction is confirmed on chain with a success status and the page shows the result. A deposit whose transaction reverts sends earn_deposit_failed (reverted) instead, never both.

     */
    @AnyThread
    override fun earnDepositSuccess(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        amountUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "amount_usd" to amountUsd
        )
        trackEvent("earn_deposit_success", props)
    }

    /**
     * earn_deposit_failed
     *
     * Fired when a deposit the user confirmed does not complete: the user declines in the wallet, the wallet fails, the page cannot prepare the transaction, or the transaction reverts on chain.

     */
    @AnyThread
    override fun earnDepositFailed(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        errorCode: String,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "error_code" to errorCode
        )
        errorMessage?.let { props["error_message"] = it }
        trackEvent("earn_deposit_failed", props)
    }

    /**
     * earn_withdraw_started
     *
     * Fired when the user opens the withdrawal form of a vault.
     */
    @AnyThread
    override fun earnWithdrawStarted(isExternalWebView: Int, eventProvider: String, vaultId: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId
        )
        trackEvent("earn_withdraw_started", props)
    }

    /**
     * earn_withdraw_amount_entered
     *
     * Fired when the user continues from the withdrawal form with an amount — once per continue, not on every keystroke.

     */
    @AnyThread
    override fun earnWithdrawAmountEntered(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        amountUsd: Double,
        isMax: Boolean
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "amount_usd" to amountUsd,
            "is_max" to isMax
        )
        trackEvent("earn_withdraw_amount_entered", props)
    }

    /**
     * earn_withdraw_review_shown
     *
     * Fired when the page shows the review of a withdrawal.
     */
    @AnyThread
    override fun earnWithdrawReviewShown(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        estimatedGasUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "estimated_gas_usd" to estimatedGasUsd
        )
        trackEvent("earn_withdraw_review_shown", props)
    }

    /**
     * earn_withdraw_confirm_tapped
     *
     * Fired when the user confirms a withdrawal on the review; the page then asks the wallet to sign (earn_sign_request), and the withdrawal ends in earn_withdraw_success or earn_withdraw_failed. A withdrawal that waits reports earn_withdraw_requested in between and stays open until the user claims its funds.

     */
    @AnyThread
    override fun earnWithdrawConfirmTapped(isExternalWebView: Int, eventProvider: String, vaultId: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId
        )
        trackEvent("earn_withdraw_confirm_tapped", props)
    }

    /**
     * earn_withdraw_requested
     *
     * Fired, for a vault whose withdrawals wait, once the request transaction (`request` in vaults.fyi) is confirmed on chain with a success status. The funds reach the wallet only after the waiting period, when the user claims them (earn_withdraw_success). A vault that pays out at once skips this event.

     */
    @AnyThread
    override fun earnWithdrawRequested(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        amountUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "amount_usd" to amountUsd
        )
        trackEvent("earn_withdraw_requested", props)
    }

    /**
     * earn_withdraw_success
     *
     * Fired when the withdrawn funds are in the wallet — once the transaction that pays them out is confirmed on chain with a success status: the withdrawal itself for a vault that pays out at once, or the claim of a requested withdrawal after its waiting period (`claim-redeem` in vaults.fyi, after earn_withdraw_requested). A reverted transaction sends earn_withdraw_failed (reverted) instead, never both.

     */
    @AnyThread
    override fun earnWithdrawSuccess(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        amountUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "amount_usd" to amountUsd
        )
        trackEvent("earn_withdraw_success", props)
    }

    /**
     * earn_withdraw_failed
     *
     * Fired when a withdrawal the user confirmed does not complete — for a withdrawal that waits, either its request or the later claim of its funds: the user declines in the wallet, the wallet fails, the page cannot prepare the transaction, or the transaction reverts on chain.

     */
    @AnyThread
    override fun earnWithdrawFailed(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        errorCode: String,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "error_code" to errorCode
        )
        errorMessage?.let { props["error_message"] = it }
        trackEvent("earn_withdraw_failed", props)
    }

    /**
     * earn_claim_started
     *
     * Fired when the user starts claiming the rewards of a vault: incentive tokens paid on top of the vault's yield (`claim-rewards` in vaults.fyi). Claiming a requested withdrawal is earn_withdraw_success, not this event. The claim ends in earn_claim_success or earn_claim_failed.

     */
    @AnyThread
    override fun earnClaimStarted(isExternalWebView: Int, eventProvider: String, vaultId: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId
        )
        trackEvent("earn_claim_started", props)
    }

    /**
     * earn_claim_success
     *
     * Fired once the claim transaction is confirmed on chain with a success status and the page shows the result. A reverted claim sends earn_claim_failed (reverted) instead, never both.

     */
    @AnyThread
    override fun earnClaimSuccess(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        rewardAmountUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "reward_amount_usd" to rewardAmountUsd
        )
        trackEvent("earn_claim_success", props)
    }

    /**
     * earn_claim_failed
     *
     * Fired when a rewards claim the user started does not complete: the user declines in the wallet, the wallet fails, the page cannot prepare the transaction, or the transaction reverts on chain.

     */
    @AnyThread
    override fun earnClaimFailed(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        errorCode: String,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "error_code" to errorCode
        )
        errorMessage?.let { props["error_message"] = it }
        trackEvent("earn_claim_failed", props)
    }

    /**
     * earn_position_viewed
     *
     * Fired when the page shows one of the user's positions: the user opens it from the positions screen, or a vault the user holds a position in opens — from the list or right on its route.

     */
    @AnyThread
    override fun earnPositionViewed(
        isExternalWebView: Int,
        eventProvider: String,
        vaultId: String,
        balanceUsd: Double,
        accruedYieldUsd: Double
    ) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "vault_id" to vaultId,
            "balance_usd" to balanceUsd,
            "accrued_yield_usd" to accruedYieldUsd
        )
        trackEvent("earn_position_viewed", props)
    }

    /**
     * earn_history_viewed
     *
     * Fired when the user opens the history of their operations.
     */
    @AnyThread
    override fun earnHistoryViewed(isExternalWebView: Int, eventProvider: String) {
        trackEvent("earn_history_viewed", hashMapOf("isExternalWebView" to isExternalWebView, "eventProvider" to eventProvider))
    }

    /**
     * earn_external_link_clicked
     *
     * Fired when the user opens an outside link from the page: the terms, the risk disclosure, the documentation, a protocol's site or an explorer.

     */
    @AnyThread
    override fun earnExternalLinkClicked(isExternalWebView: Int, eventProvider: String, url: String) {
        val props = hashMapOf(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "url" to url
        )
        trackEvent("earn_external_link_clicked", props)
    }

    /**
     * earn_webview_error
     *
     * Fired when something fails inside the loaded page — a request, the page's server, or its own script — and, once per page load, when the page has no wallet it can work with (wallet_unavailable). A page that does not load at all is the wallet's dapp_app_load_failed.

     */
    @AnyThread
    override fun earnWebviewError(
        isExternalWebView: Int,
        eventProvider: String,
        errorType: String,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "isExternalWebView" to isExternalWebView,
            "eventProvider" to eventProvider,
            "error_type" to errorType
        )
        errorMessage?.let { props["error_message"] = it }
        trackEvent("earn_webview_error", props)
    }
}

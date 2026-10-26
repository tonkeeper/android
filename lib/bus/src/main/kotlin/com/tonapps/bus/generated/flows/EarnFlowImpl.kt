package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.EarnFlow.EarnEntryViewFrom
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowChain
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowExitMethod
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowMethod
import com.tonapps.bus.generated.Events.EarnFlow.EarnSignRequestFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class EarnFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.EarnFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * earn_entry_view
     *
     * Fired when an Earn entry point is shown: the carousel of featured vaults or the block of the wallet's own vaults on the wallet main screen, or the APY badge on an asset page. Once per appearance of the screen that holds it — coming back to the screen or switching to another wallet is a new appearance, scrolling and redrawing are not. Not sent while the entry point is still waiting for its data, nor for the row that offers a retry when the wallet's positions could not be loaded at all; a block drawn from the last saved positions after a failed refresh is sent. Together with dapp_app_click (from = the same value) this yields CTR per entry point; unique viewers come from the auto-attached user id. `vaults_count` is the number of cards in the carousel (earn_featured) or of position rows (earn_your_vaults) and is absent for earn_apy, which always leads to one vault. `balance_usd` is set for earn_your_vaults only. `asset` is set for earn_apy only — the asset whose page shows the badge.

     */
    @AnyThread
    override fun earnEntryView(
        from: EarnEntryViewFrom,
        vaultsCount: Int?,
        balanceUsd: Double?,
        asset: String?
    ) {
        val props = hashMapOf<String, Any>("from" to from.key)
        vaultsCount?.let { props["vaults_count"] = it }
        balanceUsd?.let { props["balance_usd"] = it }
        asset?.let { props["asset"] = it }
        trackEvent("earn_entry_view", props)
    }

    /**
     * earn_sign_request
     *
     * Fired when the Earn page asks the wallet to sign through the injected `window.ethereum`: every `eth_sendTransaction`, `personal_sign` and `eth_signTypedData_v4` call the wallet can read, before it validates the request or asks for the passcode. Exactly one of earn_sign_success, earn_sign_rejected or earn_sign_failed follows each request. A call whose parameters cannot be read at all is refused without events, and the other provider calls — accounts, chain id, chain switch — are not sent. `from` is the value the page was opened with on dapp_app_click, so a request is attributable to an entry point without a session join; it is a non-Earn value such as browser or deep_link when the page was reached some other way. `chain` is the chain the provider is on. `vault_id` is the vault the page is on (EarnVaultId), absent when the page is not on a vault route — which is why `chain` stays a property of its own.

     */
    @AnyThread
    override fun earnSignRequest(
        from: EarnSignRequestFrom,
        method: EarnFlowMethod,
        chain: EarnFlowChain,
        vaultId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "method" to method.key,
            "chain" to chain.key
        )
        vaultId?.let { props["vault_id"] = it }
        trackEvent("earn_sign_request", props)
    }

    /**
     * earn_sign_success
     *
     * Fired when the wallet has signed an Earn request and handed the result to the page. For send_transaction the transaction was broadcast and the page got its hash; the same submission also emits transaction_sent with initiated_by = evm_injected, which records the submission: its asset is the chain's coin and its amount the transaction's value, 0 for the contract calls a deposit, withdrawal or claim makes. The amount and the vault of the operation are on the page's *_success events. For the message methods the page got the signature. Properties repeat the matching earn_sign_request.

     */
    @AnyThread
    override fun earnSignSuccess(
        from: EarnSignRequestFrom,
        method: EarnFlowMethod,
        chain: EarnFlowChain,
        vaultId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "method" to method.key,
            "chain" to chain.key
        )
        vaultId?.let { props["vault_id"] = it }
        trackEvent("earn_sign_success", props)
    }

    /**
     * earn_sign_rejected
     *
     * Fired when the user declines an Earn request by dismissing the passcode prompt; the page gets the standard 4001 (user rejected the request) error. A request has no timeout — it waits until the user answers. Properties repeat the matching earn_sign_request.

     */
    @AnyThread
    override fun earnSignRejected(
        from: EarnSignRequestFrom,
        method: EarnFlowMethod,
        chain: EarnFlowChain,
        vaultId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "method" to method.key,
            "chain" to chain.key
        )
        vaultId?.let { props["vault_id"] = it }
        trackEvent("earn_sign_rejected", props)
    }

    /**
     * earn_sign_failed
     *
     * Fired when an Earn request fails and the page gets an error other than 4001: the request cannot be carried out, or the transaction could not be built, signed or broadcast. A transaction that is broadcast and then reverts on chain counts as earn_sign_success — the wallet does not follow a transaction after broadcasting it. Properties other than `error_code` and `error_message` repeat the matching earn_sign_request.

     */
    @AnyThread
    override fun earnSignFailed(
        from: EarnSignRequestFrom,
        method: EarnFlowMethod,
        chain: EarnFlowChain,
        vaultId: String?,
        errorCode: String,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "method" to method.key,
            "chain" to chain.key,
            "error_code" to errorCode
        )
        vaultId?.let { props["vault_id"] = it }
        errorMessage?.let { props["error_message"] = it }
        trackEvent("earn_sign_failed", props)
    }

    /**
     * earn_close
     *
     * Fired when the Earn page is closed and the user is back on the wallet screen that opened it. Not sent when the app is terminated with the page open. `from` is the value the page was opened with on dapp_app_click.

     */
    @AnyThread
    override fun earnClose(from: EarnSignRequestFrom, exitMethod: EarnFlowExitMethod, durationMs: Int) {
        val props = hashMapOf(
            "from" to from.key,
            "exit_method" to exitMethod.key,
            "duration_ms" to durationMs
        )
        trackEvent("earn_close", props)
    }
}

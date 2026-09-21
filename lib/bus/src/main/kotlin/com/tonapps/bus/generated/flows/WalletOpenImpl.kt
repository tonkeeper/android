package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletInterface
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletMode
import com.tonapps.bus.generated.Events.WalletOpen.WalletOpenWalletSource

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class WalletOpenImpl(
    private val eventExecutor: EventExecutor,
) : Events.WalletOpen {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * wallet_open
     *
     * Triggered when the user opens a wallet main screen; send again if the user switches to another wallet. The main screen lists all assets across all chains — it is not token-specific — so it carries no `asset` and no aggregate `wallet_network`; only wallet-level identity (`wallet_mode`, `wallet_source`, and the optional `wallet_interface`).

     */
    @AnyThread
    override fun walletOpen(
        walletMode: WalletOpenWalletMode,
        walletSource: WalletOpenWalletSource,
        walletInterface: WalletOpenWalletInterface?
    ) {
        val props = hashMapOf<String, Any>("wallet_mode" to walletMode.key, "wallet_source" to walletSource.key)
        walletInterface?.let { props["wallet_interface"] = it.key }
        trackEvent("wallet_open", props)
    }
}

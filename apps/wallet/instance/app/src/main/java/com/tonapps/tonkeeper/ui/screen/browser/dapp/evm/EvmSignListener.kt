package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.chainkit.core.chain.model.account.Chain

/** What became of a signing request the page made through `window.ethereum`. */
internal interface EvmSignListener {

    fun onRequested(method: String, chain: Chain.Evm)

    fun onSigned(method: String, chain: Chain.Evm)

    fun onRejected(method: String, chain: Chain.Evm)

    fun onFailed(method: String, chain: Chain.Evm, code: Int, message: String)
}
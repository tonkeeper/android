package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Message.Request

/** What became of a signing request the page made through `window.ethereum`. */
internal interface EvmSignListener {

    fun onRequested(request: Request, chain: Chain.Evm)

    fun onSigned(request: Request, chain: Chain.Evm)

    fun onRejected(request: Request, chain: Chain.Evm)

    fun onFailed(request: Request, chain: Chain.Evm, code: Int, message: String)
}

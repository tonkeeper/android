package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowChain
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowExitMethod
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowMethod
import com.tonapps.bus.generated.Events.EarnFlow.EarnSignRequestFrom
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmSignListener

internal class EarnDAppAnalytics(openedFrom: DappAppClickFrom) : EvmSignListener {

    private val events: Events.EarnFlow
        get() = AnalyticsHelper.Default.events.earnFlow

    private val from = EarnSignRequestFrom.entries.firstOrNull { it.key == openedFrom.key }
        ?: EarnSignRequestFrom.Other

    override fun onRequested(method: String, chain: Chain.Evm) {
        val signMethod = method(method) ?: return
        events.earnSignRequest(from, signMethod, chain(chain), vaultId = null)
    }

    override fun onSigned(method: String, chain: Chain.Evm) {
        val signMethod = method(method) ?: return
        events.earnSignSuccess(from, signMethod, chain(chain), vaultId = null)
    }

    override fun onRejected(method: String, chain: Chain.Evm) {
        val signMethod = method(method) ?: return
        events.earnSignRejected(from, signMethod, chain(chain), vaultId = null)
    }

    override fun onFailed(method: String, chain: Chain.Evm, code: Int, message: String) {
        val signMethod = method(method) ?: return
        events.earnSignFailed(
            from = from,
            method = signMethod,
            chain = chain(chain),
            vaultId = null,
            errorCode = code.toString(),
            errorMessage = message,
        )
    }

    fun closed(exitMethod: EarnFlowExitMethod, durationMs: Int) {
        events.earnClose(from, exitMethod, durationMs)
    }

    internal companion object {

        fun method(method: String): EarnFlowMethod? {
            return when (method) {
                "eth_sendTransaction" -> EarnFlowMethod.SendTransaction
                "personal_sign" -> EarnFlowMethod.PersonalSign
                "eth_signTypedData_v4" -> EarnFlowMethod.SignTypedData
                else -> {
                    L.w("No Earn sign method for $method")
                    null
                }
            }
        }

        fun chain(chain: Chain.Evm): EarnFlowChain {
            return when (chain.coinAssetId.substringBefore('/')) {
                "eth" -> EarnFlowChain.Eth
                "base" -> EarnFlowChain.Base
                "arb" -> EarnFlowChain.Arb
                "bsc" -> EarnFlowChain.Bnb
                "polygon" -> EarnFlowChain.Pol
                else -> EarnFlowChain.Multichain
            }
        }
    }
}
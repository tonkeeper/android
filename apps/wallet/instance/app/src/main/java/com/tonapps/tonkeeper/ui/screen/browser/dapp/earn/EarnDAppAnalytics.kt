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
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Message.Request
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmSignListener
import java.net.URI
import java.net.URISyntaxException

internal class EarnDAppAnalytics(openedFrom: DappAppClickFrom) : EvmSignListener {

    private val events: Events.EarnFlow
        get() = AnalyticsHelper.Default.events.earnFlow

    private val from = EarnSignRequestFrom.entries.firstOrNull { it.key == openedFrom.key }
        ?: EarnSignRequestFrom.Other

    override fun onRequested(request: Request, chain: Chain.Evm) {
        val signMethod = method(request.method) ?: return
        events.earnSignRequest(from, signMethod, chain(chain), vaultId(request.pageUrl))
    }

    override fun onSigned(request: Request, chain: Chain.Evm) {
        val signMethod = method(request.method) ?: return
        events.earnSignSuccess(from, signMethod, chain(chain), vaultId(request.pageUrl))
    }

    override fun onRejected(request: Request, chain: Chain.Evm) {
        val signMethod = method(request.method) ?: return
        events.earnSignRejected(from, signMethod, chain(chain), vaultId(request.pageUrl))
    }

    override fun onFailed(request: Request, chain: Chain.Evm, code: Int, message: String) {
        val signMethod = method(request.method) ?: return
        events.earnSignFailed(
            from = from,
            method = signMethod,
            chain = chain(chain),
            vaultId = vaultId(request.pageUrl),
            errorCode = code.toString(),
            errorMessage = message,
        )
    }

    fun closed(exitMethod: EarnFlowExitMethod, durationMs: Long) {
        events.earnClose(from, exitMethod, durationMs)
    }

    internal companion object {

        private const val EARN_SEGMENT = "earn"
        private const val VAULT_ROUTE_SIZE = 3

        /** The vault of an `/earn/{network}/{address}` page as its Asset ID, null on any other page. */
        fun vaultId(pageUrl: String?): String? {
            val path = try {
                pageUrl?.let { URI(it).path }
            } catch (_: URISyntaxException) {
                null
            } ?: return null
            val segments = path.split('/').filter { it.isNotEmpty() }
            val earnIndex = segments.lastIndexOf(EARN_SEGMENT)
            if (earnIndex < 0 || segments.size != earnIndex + VAULT_ROUTE_SIZE) {
                return null
            }
            val chainAndType = when (segments[earnIndex + 1].lowercase()) {
                "mainnet" -> "eth/mainnet/erc20"
                "base" -> "base/mainnet/erc20"
                "arbitrum" -> "arb/mainnet/erc20"
                "bsc" -> "bsc/mainnet/bep20"
                else -> return null
            }
            return "$chainAndType/${segments[earnIndex + 2].lowercase()}"
        }

        fun method(method: String): EarnFlowMethod? {
            return when (method) {
                EvmProviderProtocol.METHOD_SEND_TRANSACTION -> EarnFlowMethod.SendTransaction
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
                "bsc" -> EarnFlowChain.Bsc
                "polygon" -> EarnFlowChain.Polygon
                "avax" -> EarnFlowChain.Avax
                "robinhood" -> EarnFlowChain.Robinhood
                else -> EarnFlowChain.Multichain
            }
        }
    }
}

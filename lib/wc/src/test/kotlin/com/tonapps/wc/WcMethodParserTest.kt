package com.tonapps.wc

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.models.WcAppData
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcMethod
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WcMethodParserTest {

    private val adapter = RecordingAdapter()

    private val parser = WcMethodParser(Json, adapter)

    @Test
    fun `a decimal typed data chain id matching the chain reaches signing`() {
        signTypedData(chain = Chain.Smartchain.Mainnet, chainId = "56")
        signTypedData(chain = Chain.Base.Mainnet, chainId = "\"8453\"")

        assertEquals(listOf(Chain.Smartchain.Mainnet, Chain.Base.Mainnet), adapter.chains)
    }

    @Test
    fun `a hex typed data chain id matching the chain reaches signing`() {
        signTypedData(chain = Chain.Ethereum.Mainnet, chainId = "\"0x1\"")
        signTypedData(chain = Chain.Smartchain.Mainnet, chainId = "\"0x38\"")

        assertEquals(listOf(Chain.Ethereum.Mainnet, Chain.Smartchain.Mainnet), adapter.chains)
    }

    @Test
    fun `a typed data chain id of another chain is refused before signing`() {
        val result = signTypedData(chain = Chain.Ethereum.Mainnet, chainId = "\"0x2105\"")

        assertEquals(WcMethodParser.Result.Error(WcMethodParser.Result.Error.ERROR_PARSE), result)
        assertTrue(adapter.chains.isEmpty())
    }

    private fun signTypedData(chain: Chain, chainId: String): WcMethodParser.Result? {
        val params = buildJsonArray {
            add(JsonPrimitive(ADDRESS))
            add(JsonPrimitive("""{"domain":{"chainId":$chainId},"message":{}}"""))
        }
        return parser.handleRequest(
            requestId = "1",
            walletId = "wallet",
            chain = chain,
            params = params.toString(),
            method = WcMethod.EthSignTypedDataV4,
            meta = WcDappConnection(topic = "topic", app = WcAppData(name = "dApp", url = "https://dapp.example")),
        )
    }

    private class RecordingAdapter : WcRouteAdapter {

        val chains = mutableListOf<Chain>()

        override fun <T : WcRequestData> convertRequest(
            requestId: String,
            walletId: String,
            chain: Chain,
            meta: WcDappConnection,
            rawRequest: T,
        ): ConfirmRequest? {
            chains += chain
            return null
        }

        override fun handleMultiSession(
            requestId: String,
            proposal: WcSessionProposal,
            wcConnection: WcConnection,
            validation: WcValidation,
        ): DappConnectRequest = error("Not used")
    }

    private companion object {
        const val ADDRESS = "0x8ba1f109551bD432803012645Ac136ddd64DBA72"
    }
}

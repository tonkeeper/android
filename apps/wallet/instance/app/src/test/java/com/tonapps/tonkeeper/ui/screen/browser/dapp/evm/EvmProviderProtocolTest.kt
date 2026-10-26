package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Message
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Response
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EvmProviderProtocolTest {

    private val protocol = EvmProviderProtocol(Script { "" })

    // The channel keeps a reply port under route(request).id and looks it up by replyId(answer),
    // and the page matches the answer by its JSON id: all three must name the same request.
    @Test
    fun `an answer reaches the request it answers`() {
        val request = protocol.parse("""{"id":7,"method":"eth_requestAccounts","params":[]}""", "dapp.example")
            as Message.Request
        val route = protocol.route(request) as Protocol.Route.Request

        for (response in listOf(
            Response.Result(request.id, null),
            Response.Failure(request.id, 4001, "rejected"),
            Response.ChainSwitched(request.id, "0x1"),
        )) {
            assertEquals(route.id, protocol.replyId(response))
            assertEquals(7L, JSONObject(protocol.encode(response)).getLong("id"))
        }
    }
}
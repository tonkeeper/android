package com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Message
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Response
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeBridgeProtocolTest {

    private val protocol = NativeBridgeProtocol(config = { JSONObject() })

    @Test
    fun `ready opens the document's session`() {
        val message = protocol.parse("""{"type":"ready"}""", "earn.example")

        assertEquals(Message.Ready, message)
        assertEquals(Protocol.Route.Handshake, protocol.route(Message.Ready))
    }

    @Test
    fun `a request without an id is ignored`() {
        assertNull(protocol.parse("""{"method":"ui.close"}""", "earn.example"))
    }

    @Test
    fun `a malformed message is ignored`() {
        assertNull(protocol.parse("not json", "earn.example"))
    }

    @Test
    fun `an event goes to the session and carries its payload`() {
        val event = Response.Event("config.themeChanged", JSONObject().put("theme", "light"))

        val json = JSONObject(protocol.encode(event))

        assertNull(protocol.replyId(event))
        assertEquals("event", json.getString("type"))
        assertEquals("config.themeChanged", json.getString("event"))
        assertEquals("light", json.getJSONObject("payload").getString("theme"))
    }
}

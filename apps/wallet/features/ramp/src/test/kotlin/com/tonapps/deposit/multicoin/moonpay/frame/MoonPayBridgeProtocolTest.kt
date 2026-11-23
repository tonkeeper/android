package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.test.silenceLog
import com.tonapps.webview.bridge.InAppBridgeContract.Protocol
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class MoonPayBridgeProtocolTest {

    private val protocol = MoonPayBridgeProtocol(channelId = "ch_1")

    @Test
    fun `an envelope survives a round trip`() {
        val message = MoonPayFrameMessage(
            meta = MoonPayFrameMessage.Meta("ch_1"),
            kind = "complete",
            payload = buildJsonObject {
                put("status", "active")
                put("mismatch", true)
                put("count", 2)
            },
        )

        val decoded = MoonPayFrameMessage.decode(message.encode())

        assertEquals(message, decoded)
        assertEquals(2, decoded.version)
    }

    @Test
    fun `a missing version is the protocol version`() {
        val message = MoonPayFrameMessage.decode("""{"meta":{"channelId":"ch_1"},"kind":"ready"}""")

        assertEquals(MoonPayFrameMessage.PROTOCOL_VERSION, message.version)
        assertNull(message.payload)
    }

    @Test
    fun `ack carries no payload`() {
        assertEquals(
            """{"version":2,"meta":{"channelId":"ch_1"},"kind":"ack"}""",
            protocol.encode(MoonPayFrameCommand.Ack),
        )
    }

    @Test
    fun `setQuote carries the signature`() {
        val message = MoonPayFrameCommand.SetQuote("sig").message("ch_1")

        assertEquals("setQuote", message.kind)
        assertEquals(
            buildJsonObject { putJsonObject("quote") { put("signature", "sig") } },
            message.payload,
        )
    }

    @Test
    fun `a message of this channel becomes an event`() {
        assertEquals(
            MoonPayFrameEvent.Ready,
            protocol.parse("""{"version":2,"meta":{"channelId":"ch_1"},"kind":"ready"}""", HOST, null),
        )
    }

    @Test
    fun `a message of another channel is dropped`() {
        assertNull(protocol.parse("""{"version":2,"meta":{"channelId":"other"},"kind":"ready"}""", HOST, null))
    }

    @Test
    fun `a malformed message is dropped`() {
        assertNull(protocol.parse("{not json", HOST, null))
        assertNull(protocol.parse("42", HOST, null))
        assertNull(protocol.parse("""{"meta":{"channelId":"ch_1"},"kind":"challenge","payload":{}}""", HOST, null))
    }

    @Test
    fun `only a handshake opens the session`() {
        assertEquals(Protocol.Route.Handshake, protocol.route(MoonPayFrameEvent.Handshake))
        assertEquals(Protocol.Route.Event, protocol.route(MoonPayFrameEvent.Ready))
        assertEquals(
            Protocol.Route.Event,
            protocol.route(MoonPayFrameEvent.Unknown("somethingElse", JsonPrimitive(1))),
        )
    }

    @Test
    fun `every command goes to the session`() {
        assertNull(protocol.replyId(MoonPayFrameCommand.Ack))
        assertNull(protocol.replyId(MoonPayFrameCommand.SetQuote("sig")))
    }

    private companion object {

        const val HOST = "platform.moonpay.com"

        @JvmStatic
        @BeforeClass
        fun setUpLog() {
            silenceLog()
        }
    }
}

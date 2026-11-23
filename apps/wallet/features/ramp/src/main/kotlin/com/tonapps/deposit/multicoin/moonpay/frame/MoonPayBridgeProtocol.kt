package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.log.L
import com.tonapps.webview.bridge.InAppBridgeContract.Protocol
import com.tonapps.webview.bridge.InAppBridgeContract.Script

class MoonPayBridgeProtocol(
    private val channelId: String,
) : Protocol<MoonPayFrameEvent, MoonPayFrameCommand> {

    override val name: String = BRIDGE_NAME

    override val script: Script = Script { FORWARD_REPLIES_TO_WINDOW_SCRIPT }

    override fun parse(data: String, host: String, pageUrl: String?): MoonPayFrameEvent? {
        return try {
            val message = MoonPayFrameMessage.decode(data)
            if (message.channelId != channelId) {
                L.d("$BRIDGE_NAME: ignored a message for another channel")
                return null
            }
            MoonPayFrameEvent.from(message)
        } catch (e: MoonPayFrameException.Malformed) {
            L.w("$BRIDGE_NAME: ${e.message}")
            null
        }
    }

    override fun encode(response: MoonPayFrameCommand): String = response.message(channelId).encode()

    override fun route(message: MoonPayFrameEvent): Protocol.Route {
        return when (message) {
            is MoonPayFrameEvent.Handshake -> Protocol.Route.Handshake
            is MoonPayFrameEvent.Ready,
            is MoonPayFrameEvent.Complete,
            is MoonPayFrameEvent.TransactionCreated,
            is MoonPayFrameEvent.Challenge,
            is MoonPayFrameEvent.Cancelled,
            is MoonPayFrameEvent.ButtonPressed,
            is MoonPayFrameEvent.Resolved,
            is MoonPayFrameEvent.Error,
            is MoonPayFrameEvent.Unknown,
            -> Protocol.Route.Event
        }
    }

    override fun replyId(response: MoonPayFrameCommand): String? = null

    override fun describeMessage(message: MoonPayFrameEvent): String {
        return when (message) {
            is MoonPayFrameEvent.Unknown -> "Unknown ${message.kind}"
            is MoonPayFrameEvent.Handshake,
            is MoonPayFrameEvent.Ready,
            is MoonPayFrameEvent.Complete,
            is MoonPayFrameEvent.TransactionCreated,
            is MoonPayFrameEvent.Challenge,
            is MoonPayFrameEvent.Cancelled,
            is MoonPayFrameEvent.ButtonPressed,
            is MoonPayFrameEvent.Resolved,
            is MoonPayFrameEvent.Error,
            -> super.describeMessage(message)
        }
    }

    private companion object {

        const val BRIDGE_NAME = "MoonPayBridge"

        const val FORWARD_REPLIES_TO_WINDOW_SCRIPT = """
            (function () {
                var bridge = window.$BRIDGE_NAME;
                if (!bridge) {
                    return;
                }
                bridge.onmessage = function (event) {
                    window.postMessage(event.data, '*');
                };
            })();
        """
    }
}

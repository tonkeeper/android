package com.tonapps.webview.bridge

import com.tonapps.webview.bridge.InAppBridgeContract.MessageHandler
import com.tonapps.webview.bridge.InAppBridgeContract.Protocol
import com.tonapps.webview.bridge.transport.InAppTransport

class InAppBridge<In : Any, Out : Any>(
    private val protocol: Protocol<In, Out>,
    val handler: MessageHandler<In, Out>,
) {

    val name: String
        get() = protocol.name

    fun connection(transport: InAppTransport): InAppBridgeConnection<In, Out> {
        return InAppBridgeConnection(InAppBridgeChannel(protocol, transport), handler)
    }
}
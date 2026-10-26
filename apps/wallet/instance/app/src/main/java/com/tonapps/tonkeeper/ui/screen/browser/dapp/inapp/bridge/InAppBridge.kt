package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.MessageHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.transport.InAppTransport

internal class InAppBridge<In : Any, Out : Any>(
    private val protocol: Protocol<In, Out>,
    val handler: MessageHandler<In, Out>,
) {

    val name: String
        get() = protocol.name

    fun connection(transport: InAppTransport): InAppBridgeConnection<In, Out> {
        return InAppBridgeConnection(InAppBridgeChannel(protocol, transport), handler)
    }
}
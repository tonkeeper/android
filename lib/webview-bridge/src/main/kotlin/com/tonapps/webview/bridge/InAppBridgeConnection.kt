package com.tonapps.webview.bridge

import androidx.annotation.MainThread
import com.tonapps.webview.bridge.InAppBridgeContract.MessageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** One bridge wired to its page, from attach to detach. */
class InAppBridgeConnection<In : Any, Out : Any>(
    private val channel: InAppBridgeChannel<In, Out>,
    private val handler: MessageHandler<In, Out>,
) {

    private var attached = false

    @MainThread
    suspend fun attach(scope: CoroutineScope): Boolean {
        if (!channel.attach()) {
            return false
        }
        attached = true
        handler.attach()
        channel.messages.onEach { handler.onMessage(it) }.launchIn(scope)
        handler.responses.onEach { channel.send(it) }.launchIn(scope)
        return true
    }

    /** Safe on a connection that never attached. */
    @MainThread
    fun detach() {
        if (!attached) {
            return
        }
        attached = false
        // Answered while the reply ports are still there: after detach nothing reaches the page.
        handler.detach().forEach(channel::send)
        channel.detach()
    }
}
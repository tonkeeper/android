package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.MessageHandler
import uikit.extensions.collectFlow

/** One bridge wired to its page, from attach to detach. */
internal class InAppBridgeConnection<In : Any, Out : Any>(
    private val channel: InAppBridgeChannel<In, Out>,
    private val handler: MessageHandler<In, Out>,
) {

    private var attached = false

    @MainThread
    suspend fun attach(owner: LifecycleOwner): Boolean {
        if (!channel.attach()) {
            return false
        }
        attached = true
        handler.attach()
        owner.collectFlow(channel.messages) { handler.onMessage(it) }
        owner.collectFlow(handler.responses) { channel.send(it) }
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
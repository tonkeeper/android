package com.tonapps.webview.bridge.transport

/**
 * Moves raw strings between a page and native code. One instance serves one bridge in one WebView:
 * it installs the bridge's script and listens under the bridge's name until [detach].
 *
 * Everything runs on the main thread, [Receiver] included: a transport whose platform callback
 * comes on another thread moves it to main itself.
 */
interface InAppTransport {

    class Endpoint(val name: String, val script: String)

    fun interface Receiver {
        /** [host] is the host of the frame that sent [data], [pageUrl] the URL the page is on. */
        fun onMessage(data: String, host: String, pageUrl: String?, reply: Reply)
    }

    /** The way back to the document that sent a message. */
    fun interface Reply {
        fun post(data: String)
    }

    fun attach(endpoint: Endpoint, receiver: Receiver): Boolean

    /** Safe to call on a transport that was never attached or failed to attach. */
    fun detach()
}
package com.tonapps.webview.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

/**
 * What a web page the app hosts implements to talk to native code: an in-app dApp, a payment
 * provider's frame. The page gets one or more bridges; for each one:
 *
 * 1. a [Protocol]: the wire format and the page script that speaks it;
 * 2. a [MessageHandler]: what native code does with the page's messages, and the [Effect]s it needs
 *    from the screen;
 * 3. an [InAppBridge] that pairs the two. A screen with several bridges lists them in a
 *    [BaseBridges] subclass; its view model delegates [Bridges] to it and starts it on its own scope.
 *
 * How the strings travel and reach the right document is not part of this: see InAppBridgeChannel
 * and InAppTransport.
 */
interface InAppBridgeContract {

    interface Protocol<In : Any, Out : Any> {

        /** The global the page script talks to. */
        val name: String

        val script: Script

        /** [host] is the host of the frame that sent [data], [pageUrl] the URL the page is on. */
        fun parse(data: String, host: String, pageUrl: String?): In?

        fun encode(response: Out): String

        fun route(message: In): Route

        /** The request [response] answers, or null when it belongs to the session. */
        fun replyId(response: Out): String?

        // Logged in release builds and shared with support: method names and ids only, no params.
        fun describeMessage(message: In): String = message::class.simpleName.orEmpty()

        fun describeResponse(response: Out): String {
            val name = response::class.simpleName.orEmpty()
            return replyId(response)?.let { "$name #$it" } ?: name
        }

        sealed interface Route {

            /** Opens a new document: the reply ports of the previous one are dead. */
            data object Handshake : Route

            data class Request(val id: String) : Route

            data object Event : Route
        }
    }

    fun interface Script {
        suspend fun load(): String
    }

    interface MessageHandler<In : Any, Out : Any> {

        val responses: Flow<Out>

        val effects: Flow<Effect>
            get() = emptyFlow()

        fun attach()

        fun onMessage(message: In)

        /** Ends the session and returns what the page must still receive before the transport goes. */
        fun detach(): List<Out>
    }

    /** What a bridge needs from the screen. */
    sealed interface Effect {

        data object Close : Effect

        data class Toast(val title: String) : Effect

        data class Haptic(val style: HapticStyle) : Effect

        data class CloseButtonVisibility(val visible: Boolean) : Effect

        data class Share(val text: String) : Effect
    }

    enum class HapticStyle {
        Selection,
        Light,
        Medium,
        Heavy,
        Success,
        Warning,
        Error,
    }

    /** What the screen reads from the view model. */
    interface Bridges {

        val bridges: List<InAppBridge<*, *>>

        val effects: Flow<Effect>
    }

    /** Started by the view model on a scope of its own, so the bridges end when it is cleared. */
    abstract class BaseBridges : Bridges {

        private var started: List<InAppBridge<*, *>>? = null

        override val bridges: List<InAppBridge<*, *>>
            get() = checkNotNull(started) { "start() was not called" }

        override val effects: Flow<Effect> by lazy {
            bridges.map { it.handler.effects }.merge()
        }

        fun start(scope: CoroutineScope) {
            check(started == null) { "start() was already called" }
            started = createBridges(scope)
        }

        protected abstract fun createBridges(scope: CoroutineScope): List<InAppBridge<*, *>>
    }
}
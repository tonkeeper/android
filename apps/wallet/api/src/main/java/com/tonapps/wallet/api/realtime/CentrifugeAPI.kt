package com.tonapps.wallet.api.realtime

import com.tonapps.log.L
import io.github.centrifugal.centrifuge.Client
import io.github.centrifugal.centrifuge.ConnectedEvent
import io.github.centrifugal.centrifuge.ConnectingEvent
import io.github.centrifugal.centrifuge.ConnectionTokenEvent
import io.github.centrifugal.centrifuge.ConnectionTokenGetter
import io.github.centrifugal.centrifuge.DisconnectedEvent
import io.github.centrifugal.centrifuge.ErrorEvent
import io.github.centrifugal.centrifuge.EventListener
import io.github.centrifugal.centrifuge.Options
import io.github.centrifugal.centrifuge.PublicationEvent
import io.github.centrifugal.centrifuge.SubscribedEvent
import io.github.centrifugal.centrifuge.SubscribingEvent
import io.github.centrifugal.centrifuge.Subscription
import io.github.centrifugal.centrifuge.SubscriptionErrorEvent
import io.github.centrifugal.centrifuge.SubscriptionEventListener
import io.github.centrifugal.centrifuge.SubscriptionOptions
import io.github.centrifugal.centrifuge.SubscriptionState
import io.github.centrifugal.centrifuge.SubscriptionTokenEvent
import io.github.centrifugal.centrifuge.SubscriptionTokenGetter
import io.github.centrifugal.centrifuge.TokenCallback
import io.github.centrifugal.centrifuge.UnauthorizedException
import io.github.centrifugal.centrifuge.UnsubscribedEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val CLIENT_NAME = "android"

class CentrifugeAPI(
    private val url: String,
    private val userAgent: String,
    private val scope: CoroutineScope,
    private val connectionToken: suspend () -> RealtimeTokenResult,
) {

    private val _signals = MutableSharedFlow<RealtimeSignal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val signals: SharedFlow<RealtimeSignal> = _signals.asSharedFlow()

    private val clientDelegate = lazy { createClient() }
    private val client: Client by clientDelegate

    @Synchronized
    fun connect() {
        client.connect()
    }

    @Synchronized
    fun disconnect() {
        if (clientDelegate.isInitialized()) {
            client.disconnect()
        }
    }

    @Synchronized
    fun subscribe(channel: String, token: suspend () -> RealtimeTokenResult) {
        val existing = client.getSubscription(channel)
        if (existing != null) {
            if (existing.state == SubscriptionState.UNSUBSCRIBED) {
                existing.subscribe()
            }
            return
        }
        val options = SubscriptionOptions().apply { tokenGetter = subscriptionTokenGetter(token) }
        client.newSubscription(channel, options, subscriptionListener(channel)).subscribe()
    }

    @Synchronized
    fun unsubscribe(channel: String) {
        if (clientDelegate.isInitialized()) {
            client.getSubscription(channel)?.let(client::removeSubscription)
        }
    }

    private fun createClient(): Client {
        val options = Options().apply {
            name = CLIENT_NAME
            headers = mapOf("User-Agent" to userAgent)
            tokenGetter = connectionTokenGetter()
        }
        return Client(url, options, clientListener())
    }

    private fun connectionTokenGetter() = object : ConnectionTokenGetter() {
        override fun getConnectionToken(event: ConnectionTokenEvent, callback: TokenCallback) {
            scope.launch {
                callback.deliver(connectionToken)
            }
        }
    }

    private fun subscriptionTokenGetter(token: suspend () -> RealtimeTokenResult) = object : SubscriptionTokenGetter() {
        override fun getSubscriptionToken(event: SubscriptionTokenEvent, callback: TokenCallback) {
            scope.launch {
                callback.deliver(token)
            }
        }
    }

    private suspend fun TokenCallback.deliver(token: suspend () -> RealtimeTokenResult) {
        val result = try {
            token()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RealtimeTokenResult.Failure(e)
        }
        when (result) {
            is RealtimeTokenResult.Token -> Done(null, result.value)
            is RealtimeTokenResult.Forbidden -> Done(UnauthorizedException(), "")
            is RealtimeTokenResult.DisabledByBackend -> {
                _signals.tryEmit(RealtimeSignal.DisabledByBackend)
                Done(UnauthorizedException(), "")
            }
            is RealtimeTokenResult.Failure -> {
                L.w(result.error, "Realtime token request failed")
                Done(result.error, "")
            }
        }
    }

    private fun clientListener() = object : EventListener() {
        override fun onConnecting(client: Client, event: ConnectingEvent) {
            L.d("Realtime connecting: ${event.code} ${event.reason}")
        }

        override fun onConnected(client: Client, event: ConnectedEvent) {
            L.d("Realtime connected")
        }

        override fun onDisconnected(client: Client, event: DisconnectedEvent) {
            L.d("Realtime disconnected: ${event.code} ${event.reason}")
        }

        override fun onError(client: Client, event: ErrorEvent) {
            L.w(event.error, "Realtime client error")
        }
    }

    private fun subscriptionListener(channel: String) = object : SubscriptionEventListener() {

        private val subscribedBefore = AtomicBoolean(false)

        override fun onSubscribing(subscription: Subscription, event: SubscribingEvent) {
            L.d("Realtime subscribing: $channel ${event.code} ${event.reason}")
            _signals.tryEmit(RealtimeSignal.Unsubscribed(channel))
        }

        override fun onSubscribed(subscription: Subscription, event: SubscribedEvent) {
            L.d("Realtime subscribed: $channel")
            val resubscribed = !subscribedBefore.compareAndSet(false, true)
            _signals.tryEmit(RealtimeSignal.Subscribed(channel, resubscribed))
        }

        override fun onPublication(subscription: Subscription, event: PublicationEvent) {
            _signals.tryEmit(RealtimeSignal.Publication(channel, event.data))
        }

        override fun onUnsubscribed(subscription: Subscription, event: UnsubscribedEvent) {
            L.d("Realtime unsubscribed: $channel ${event.code} ${event.reason}")
            _signals.tryEmit(RealtimeSignal.Unsubscribed(channel))
        }

        override fun onError(subscription: Subscription, event: SubscriptionErrorEvent) {
            L.w(event.error, "Realtime subscription error: $channel")
        }
    }
}

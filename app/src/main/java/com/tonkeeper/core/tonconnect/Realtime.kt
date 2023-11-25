package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import ton.console.Network

internal class Realtime(
    context: Context
): EventSourceListener() {

    private companion object {
        private const val EVENT_ID = "event_id"
    }

    private val prefs = context.getSharedPreferences("tc_realtime", Context.MODE_PRIVATE)
    private var eventSource: EventSource? = null

    private var lastEventId: String
        get() = prefs.getString(EVENT_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(EVENT_ID, value).apply()
        }

    fun start(clientIds: List<String>) {
        if (true) {
            return
        }
        release()

        val value = clientIds.joinToString(",")
        var uri = Uri.parse("${Bridge.DEFAULT_URL}/events?client_id=$value")
        if (lastEventId != "") {
            uri = uri.buildUpon().appendQueryParameter("last_event_id", lastEventId).build()
        }

        Log.d("TonConnectLog", "uri: $uri")

        val builder = Request.Builder()
            .url(uri.toString())
            .header("Accept", "text/event-stream")

        val request = builder.build()

        val factory = EventSources.createFactory(Network.okHttpClient)
        eventSource = factory.newEventSource(request, this)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)
        Log.d("TonConnectLog", "onEvent($id): $data")
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        super.onFailure(eventSource, t, response)
        Log.d("TonConnectLog", "onFailure", t)
    }

    override fun onOpen(eventSource: EventSource, response: Response) {
        super.onOpen(eventSource, response)
        Log.d("TonConnectLog", "onOpen")
    }

    override fun onClosed(eventSource: EventSource) {
        super.onClosed(eventSource)
        Log.d("TonConnectLog", "onClosed")
    }

    fun release() {
        eventSource?.cancel()
        eventSource = null
    }
}
package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonkeeper.core.tonconnect.models.TCEvent
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import ton.console.Network

internal class Realtime(
    context: Context,
    private val onEvent: (TCEvent) -> Unit
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
        release()

        val value = clientIds.joinToString(",")
        var uri = Uri.parse("${Bridge.DEFAULT_URL}/events?client_id=$value")
        if (lastEventId != "") {
            uri = uri.buildUpon().appendQueryParameter("last_event_id", lastEventId).build()
        }

        val builder = Request.Builder()
            .url(uri.toString())
            .header("Accept", "text/event-stream")

        val request = builder.build()

        val factory = EventSources.createFactory(Network.okHttpClient)
        eventSource = factory.newEventSource(request, this)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)
        id?.let { lastEventId = it }

        try {
            onEvent(TCEvent(data))
        } catch (ignored: Throwable) { }
    }

    fun release() {
        eventSource?.cancel()
        eventSource = null
    }
}